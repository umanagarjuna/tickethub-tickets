// LoggingFilter.java in your config package
package com.tickethub.eventservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(1) // Or a low number to ensure it runs early
public class LoggingFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String authHeader = httpRequest.getHeader("Authorization");

        log.info(">>> Request to URI: {}", httpRequest.getRequestURI());
        if (authHeader != null) {
            log.info(">>> Received Raw Authorization Header: [{}]", authHeader); // Log the raw header
            if (authHeader.toLowerCase().startsWith("bearer ") && authHeader.length() > 7) {
                String token = authHeader.substring(7);
                log.debug(">>> Full received Bearer token for decoding attempt: [{}]", token); // Log the exact token
            }
        } else {
            log.info(">>> No Authorization Header received for request to: {}", httpRequest.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}