package com.emanstagram.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Reads {@code Authorization: Bearer <jwt>} and populates the security
 * context. Invalid tokens are ignored rather than rejected, so a stale token
 * on a public endpoint does not break the request.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Optional<UserDetails> user = extractToken(request)
                .flatMap(jwtService::parseAccessToken)
                .flatMap(this::toUserDetails);

        // An access token outlives a suspension by up to 15 minutes; refuse
        // it here rather than waiting for it to expire.
        if (user.isPresent() && !user.get().isAccountNonLocked()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"ACCOUNT_SUSPENDED\","
                    + "\"message\":\"This account has been suspended.\"}}");
            return;
        }
        user.ifPresent(u -> authenticate(u, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(UserDetails user, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities());
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private Optional<UserDetails> toUserDetails(Claims claims) {
        try {
            return Optional.of(userDetailsService.loadUserById(
                    java.util.UUID.fromString(claims.getSubject())));
        } catch (RuntimeException ex) {
            // Token references a deleted account.
            return Optional.empty();
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }
}
