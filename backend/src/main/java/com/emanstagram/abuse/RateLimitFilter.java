package com.emanstagram.abuse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Request rate limits, applied before any controller runs.
 *
 * <p>Anonymous endpoints (sign-up, login) are limited per IP. Signed-in
 * actions are limited per account, so a shared network such as a school or
 * office doesn't lock everyone out because of one person. A generous per-IP
 * ceiling on all of {@code /api/**} catches anything the specific rules miss.
 *
 * <p>Registered inside the security chain, after the JWT filter, so the
 * account is known. It is deliberately not a Spring bean, which would also
 * register it a second time as a plain servlet filter outside security.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    enum Scope { IP, ACCOUNT }

    /**
     * @param bucket rules sharing a bucket name share one counter, so the
     *               upload endpoints draw on a single budget
     */
    record Rule(String bucket, HttpMethod method, String pattern, int limit, Duration window, Scope scope,
                String message) {
    }

    private static final String SLOW_DOWN = "You're doing that too often. Please wait a bit and try again.";

    static final List<Rule> RULES = List.of(
            // --- anonymous, per IP ---
            new Rule("register", HttpMethod.POST, "/api/auth/register", 5, Duration.ofHours(1), Scope.IP,
                    "Too many accounts have been created from your network. Please try again later."),
            new Rule("login", HttpMethod.POST, "/api/auth/login", 10, Duration.ofMinutes(15), Scope.IP,
                    "Too many sign-in attempts. Please wait a few minutes and try again."),
            new Rule("refresh", HttpMethod.POST, "/api/auth/refresh", 60, Duration.ofMinutes(5), Scope.IP, SLOW_DOWN),
            new Rule("username-check", HttpMethod.GET, "/api/auth/username-available", 60, Duration.ofMinutes(1),
                    Scope.IP, SLOW_DOWN),

            // --- signed in, per account ---
            new Rule("password", HttpMethod.POST, "/api/me/password", 5, Duration.ofMinutes(15), Scope.ACCOUNT,
                    "Too many password attempts. Please wait a few minutes."),
            new Rule("upload", HttpMethod.POST, "/api/posts", 30, Duration.ofHours(1), Scope.ACCOUNT,
                    "You're uploading too quickly. Please wait a while before posting again."),
            new Rule("upload", HttpMethod.POST, "/api/stories", 30, Duration.ofHours(1), Scope.ACCOUNT,
                    "You're uploading too quickly. Please wait a while before posting again."),
            new Rule("upload", HttpMethod.POST, "/api/conversations/*/attachments", 30, Duration.ofHours(1),
                    Scope.ACCOUNT, "You're sending files too quickly. Please wait a while."),
            new Rule("upload", HttpMethod.PUT, "/api/me/avatar", 30, Duration.ofHours(1), Scope.ACCOUNT, SLOW_DOWN),
            new Rule("upload", HttpMethod.PUT, "/api/me/banner", 30, Duration.ofHours(1), Scope.ACCOUNT, SLOW_DOWN),
            new Rule("comment", HttpMethod.POST, "/api/posts/*/comments", 30, Duration.ofMinutes(5), Scope.ACCOUNT,
                    "You're commenting too quickly. Please slow down."),
            new Rule("message", HttpMethod.POST, "/api/conversations/*/messages", 60, Duration.ofMinutes(1),
                    Scope.ACCOUNT, "You're sending messages too quickly. Please slow down."),
            new Rule("new-chat", HttpMethod.POST, "/api/conversations/direct", 30, Duration.ofHours(1), Scope.ACCOUNT,
                    SLOW_DOWN),
            new Rule("new-chat", HttpMethod.POST, "/api/conversations/group", 30, Duration.ofHours(1), Scope.ACCOUNT,
                    SLOW_DOWN),
            new Rule("follow", HttpMethod.PUT, "/api/users/*/follow", 100, Duration.ofHours(1), Scope.ACCOUNT,
                    "You're following people too quickly. Please try again later."),
            new Rule("like", HttpMethod.PUT, "/api/posts/*/like", 300, Duration.ofHours(1), Scope.ACCOUNT, SLOW_DOWN),
            new Rule("like", HttpMethod.PUT, "/api/comments/*/like", 300, Duration.ofHours(1), Scope.ACCOUNT, SLOW_DOWN),
            new Rule("report", HttpMethod.POST, "/api/reports", 20, Duration.ofHours(1), Scope.ACCOUNT, SLOW_DOWN),
            new Rule("search", HttpMethod.GET, "/api/search", 60, Duration.ofMinutes(1), Scope.ACCOUNT, SLOW_DOWN),

            // --- catch-all ceiling, per IP ---
            new Rule("api", null, "/api/**", 600, Duration.ofMinutes(1), Scope.IP, SLOW_DOWN)
    );

    private final RateLimiter limiter;
    private final ClientIp clientIp;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(RateLimiter limiter, ClientIp clientIp) {
        this.limiter = limiter;
        this.clientIp = clientIp;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        for (Rule rule : RULES) {
            if ((rule.method() != null && !rule.method().matches(method)) || !matcher.match(rule.pattern(), path)) {
                continue;
            }
            String subject = rule.scope() == Scope.ACCOUNT ? account() : null;
            if (subject == null) {
                // Anonymous callers of an account-scoped rule are rejected
                // later by security anyway; count them per IP meanwhile.
                subject = "ip:" + clientIp.of(request);
            } else {
                subject = "user:" + subject;
            }
            long retryAfter = limiter.tryAcquire(rule.bucket() + "|" + subject, rule.limit(), rule.window());
            if (retryAfter > 0) {
                log.info("Rate limit '{}' hit by {} on {} {}", rule.bucket(), subject, method, path);
                reject(response, retryAfter, rule.message(), path);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String account() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserDetails d ? d.getUsername().toLowerCase() : null;
    }

    private static void reject(HttpServletResponse response, long retryAfter, String message, String path)
            throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"" + message
                + "\",\"path\":\"" + path.replace("\"", "") + "\",\"timestamp\":\"" + Instant.now() + "\"}}");
    }
}
