package com.alumindex.security;

import com.alumindex.common.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtService.isValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        Claims claims = jwtService.parse(token);
        String email       = claims.getSubject();
        String role        = claims.get("role",     String.class);
        String tenantIdStr = claims.get("tenantId", String.class);
        String userIdStr   = claims.get("userId",   String.class);

        // Set TenantContext — ignored by service layer for superadmin
        if (tenantIdStr != null && !tenantIdStr.isBlank()) {
            TenantContext.set(UUID.fromString(tenantIdStr));
        }

        var auth = new UsernamePasswordAuthenticationToken(
                new AlumIndexPrincipal(
                        UUID.fromString(userIdStr),
                        email,
                        role,
                        tenantIdStr != null && !tenantIdStr.isBlank()
                                ? UUID.fromString(tenantIdStr) : null),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }
}
