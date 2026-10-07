package com.example.payment.security;

import com.example.payment.exception.*;
import com.example.payment.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

public class CredentialVersionFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_POSTS = Set.of("/api/auth/register", "/api/auth/register/verify", "/api/auth/login",
            "/api/auth/otp/resend", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm");
    private final UserRepository users;
    private final ObjectMapper mapper;
    public CredentialVersionFilter(UserRepository users, ObjectMapper mapper) { this.users=users; this.mapper=mapper; }
    public static boolean publicRoute(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("POST".equals(request.getMethod()) && PUBLIC_POSTS.contains(path))
                || ("GET".equals(request.getMethod()) && (path.equals("/api/auth/csrf") || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html") || path.startsWith("/v3/api-docs")));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean valid = false;
        try {
            if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
                valid = users.findById(principal.userId()).map(user -> user.getCredentialVersion() == principal.credentialVersion()).orElse(false);
                if (!valid) {
                    SecurityContextHolder.clearContext();
                    if (request.getSession(false) != null) request.getSession(false).invalidate();
                }
            }
        } catch (RuntimeException ex) {
            ApiErrors.write(mapper, response, new ApiException(500,"INTERNAL_ERROR","Unable to validate session")); return;
        }
        if (!valid && !publicRoute(request)) { ApiErrors.write(mapper,response,ApiException.authentication()); return; }
        chain.doFilter(request,response);
    }
}
