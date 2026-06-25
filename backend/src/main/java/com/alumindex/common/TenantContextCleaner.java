package com.alumindex.common;

import jakarta.servlet.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ensures TenantContext is cleared after every request, regardless of outcome.
 * Runs last (highest order) so it wraps even the security filters.
 */
@Component
@Order(Integer.MAX_VALUE)
public class TenantContextCleaner implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
