package com.emanstagram.abuse;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cloudflare Turnstile, the bot check on sign-up.
 *
 * <p>The browser solves the challenge (usually invisibly) and gets a
 * one-time token; this checks that token with Cloudflare before an account is
 * created. Off until both keys are configured, so local development needs no
 * setup.
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final EmanstagramProperties.Security.Turnstile config;
    private final RestClient http;

    public TurnstileVerifier(EmanstagramProperties properties) {
        var security = properties.security();
        this.config = security == null ? null : security.turnstile();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (!enabled()) {
            log.warn("Turnstile is not configured: sign-up has no bot check. "
                    + "Set TURNSTILE_SITE_KEY and TURNSTILE_SECRET_KEY in production.");
        }
    }

    public boolean enabled() {
        return config != null && config.enabled();
    }

    /** The public key the browser widget needs, or null when the check is off. */
    public String siteKey() {
        return enabled() ? config.siteKey() : null;
    }

    /** Throws a 400 unless the token is valid. Does nothing when Turnstile is off. */
    public void verify(String token, String clientIp) {
        if (!enabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("CAPTCHA_REQUIRED", "Please complete the check to show you're not a bot.");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("secret", config.secretKey());
        form.add("response", token);
        if (clientIp != null) {
            form.add("remoteip", clientIp);
        }
        JsonNode result;
        try {
            result = http.post().uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException ex) {
            // Fail closed: if Cloudflare can't be reached, don't let sign-ups
            // through unchecked, which is exactly when a bot would try.
            log.warn("Turnstile verification unavailable: {}", ex.getMessage());
            throw ApiException.badRequest("CAPTCHA_UNAVAILABLE",
                    "We couldn't verify you're human right now. Please try again in a moment.");
        }
        if (result == null || !result.path("success").asBoolean(false)) {
            log.info("Turnstile rejected a sign-up: {}", result == null ? "no response" : result.path("error-codes"));
            throw ApiException.badRequest("CAPTCHA_FAILED",
                    "The bot check didn't pass. Please try it again.");
        }
    }
}
