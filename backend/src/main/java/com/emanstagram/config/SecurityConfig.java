package com.emanstagram.config;

import com.emanstagram.abuse.ClientIp;
import com.emanstagram.abuse.RateLimitFilter;
import com.emanstagram.abuse.RateLimiter;
import com.emanstagram.auth.CustomUserDetailsService;
import com.emanstagram.auth.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT security. No server-side session, so the API scales
 * horizontally and the WebSocket layer handles presence separately.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmanstagramProperties properties;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final ClientIp clientIp;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          EmanstagramProperties properties,
                          ObjectMapper objectMapper,
                          RateLimiter rateLimiter,
                          ClientIp clientIp) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationProvider authenticationProvider)
            throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)   // stateless JWT: no cookies to protect
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth
                // --- public ---
                .requestMatchers("/api/auth/register",
                                 "/api/auth/login",
                                 "/api/auth/refresh",
                                 "/api/auth/username-available",
                                 "/api/config/public").permitAll()
                // Signed-out login mosaic: public posts only.
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Static frontend assets and the SPA shell. The shell is
                // public; the API below stays protected, and RequireAuth in
                // the React router is what guards signed-in screens.
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg",
                                 "/manifest.webmanifest", "/sw.js").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/login", "/register", "/explore", "/messages", "/messages/*",
                        "/notifications", "/create", "/settings", "/saved", "/admin",
                        "/u/*", "/p/*", "/t/*", "/stories/*")
                    .permitAll()
                // --- authenticated ---
                .anyRequest().authenticated()
            )
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, ex) ->
                        writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                "You need to sign in to do that."))
                .accessDeniedHandler((request, response, ex) ->
                        writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "You do not have permission to do that."))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // After the JWT filter, so per-account limits know who is calling.
        boolean rateLimits = properties.security() == null || properties.security().rateLimitsEnabled();
        if (rateLimits) {
            http.addFilterAfter(new RateLimitFilter(rateLimiter, clientIp), JwtAuthenticationFilter.class);
        } else {
            log.warn("Rate limits are DISABLED (RATE_LIMITS_ENABLED=false). Never run production like this.");
        }

        return http.build();
    }

    /**
     * Argon2id is the current OWASP first choice.
     *
     * <p>The bean is constructed eagerly at startup rather than lazily on
     * first use, because a missing BouncyCastle only surfaces as a
     * NoClassDefFoundError deep inside a login/register request otherwise.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        // Fail fast at boot if Argon2 is unusable on this classpath.
        encoder.encode("startup-probe");
        log.info("Password hashing: Argon2id (memory 19 MiB, iterations 2)");
        return encoder;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Emits the same {@code { error: { code, message } }} shape as
     * GlobalExceptionHandler, so security failures are indistinguishable
     * from validation failures on the frontend.
     */
    private void writeError(HttpServletResponse response, HttpStatus status,
                            String code, String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var body = Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message,
                        "path", "",
                        "timestamp", Instant.now().toString()
                ));

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
