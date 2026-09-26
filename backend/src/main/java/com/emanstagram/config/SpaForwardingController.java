package com.emanstagram.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Serves the React SPA alongside the API.
 *
 * <p>Vite only emits real files for the entry point, so a hard refresh on a
 * deep link such as {@code /u/emma} or {@code /login} has no matching file on
 * disk. Forwarding those paths to {@code index.html} lets the client router
 * take over, which is what makes shared profile links survive a refresh.
 */
@Controller
public class SpaForwardingController {

    /**
     * Forwards a client-side route to the SPA shell so a hard refresh or a
     * shared link resolves correctly.
     *
     * <p>Each top-level route is listed explicitly: Spring Security's
     * {@code requestMatchers} uses Ant-style patterns, and a nested capture
     * group inside a path variable is rejected outright, so one clever regex
     * is not an option here.
     */
    @GetMapping(path = {
            "/", "/login", "/register", "/explore", "/messages",
            "/notifications", "/create", "/settings", "/u/{username}", "/p/{postId}"
    })
    public ModelAndView forward() {
        return new ModelAndView("forward:/index.html");
    }
}
