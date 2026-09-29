package com.emanstagram.abuse;

import com.emanstagram.config.EmanstagramProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * The caller's real IP address.
 *
 * <p>Behind a hosting platform's proxy, the socket address is the proxy, not
 * the user. The proxy appends the address it saw to {@code X-Forwarded-For},
 * so with N trusted proxies the client is the N-th entry from the right.
 * Entries further left were written by the client and can be anything, so
 * they are never used: taking the first entry would let anyone dodge every
 * per-IP limit by sending a made-up header.
 */
@Component
public class ClientIp {

    private final int trustedHops;

    public ClientIp(EmanstagramProperties properties) {
        var security = properties.security();
        this.trustedHops = security == null ? 0 : Math.max(0, security.trustedProxyHops());
    }

    public String of(HttpServletRequest request) {
        if (trustedHops == 0) {
            return request.getRemoteAddr();
        }
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] parts = header.split(",");
        int index = Math.max(0, parts.length - trustedHops);
        String ip = parts[index].trim();
        return ip.isEmpty() ? request.getRemoteAddr() : ip;
    }
}
