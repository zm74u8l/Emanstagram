package com.emanstagram.abuse;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shows callers the IP address the server attributes to them.
 *
 * <p>Used once after deploying to check TRUSTED_PROXY_HOPS: the value should
 * be your own public IP (what whatismyip.com shows), not an internal proxy
 * address. If it's a proxy address, every user shares one rate-limit bucket.
 * It reveals nothing a caller doesn't already know about themselves.
 */
@RestController
public class ClientIpController {

    private final ClientIp clientIp;

    public ClientIpController(ClientIp clientIp) {
        this.clientIp = clientIp;
    }

    @GetMapping("/api/public/ip")
    public Map<String, String> ip(HttpServletRequest request) {
        return Map.of("ip", clientIp.of(request));
    }
}
