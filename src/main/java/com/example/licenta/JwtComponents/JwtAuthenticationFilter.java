package com.example.licenta.JwtComponents;

import com.example.licenta.Services.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        logger.info("=== DEBUGGING shouldNotFilter ===");
        logger.info("Path: '{}', Method: '{}'", path, method);
        logger.info("Checking webhook condition: path.startsWith('/api/webhooks/') = {}", path.startsWith("/api/webhooks/"));


        if (path.equals("/api/users/register") ||
                path.equals("/api/users/login") ||
                path.equals("/api/users/verify-email") ||
                path.equals("/api/users/forgot-password") ||
                path.equals("/api/users/reset-password") ||
                path.equals("/api/users/refresh-token") ||
                path.equals("/api/users/delete-account") ||
                path.startsWith("/api/webhooks/") ||
        path.equals("/api/users/resend-verification")) {
            logger.debug("Skipping JWT filter for public user endpoint: {}", path);
            return true;
        }

        if (path.equals("/api/assistant/chat")) {
            logger.debug("Skipping JWT filter for public assistant endpoint: {}", path);
            return true;
        }

        if (path.startsWith("/api/uploads/")) {
            logger.debug("Skipping JWT filter for public uploads access: {}", path);
            return true;
        }

        if (method.equals("POST") &&
                (path.equals("/api/access/gps-checkin-guest") ||
                path.equals("/api/access/gps-checkout-guest") ||
                path.equals("/api/access/barrier/verify-entry") ||
                path.equals("/api/access/barrier/verify-exit") ||
                path.startsWith("/api/access/qr-scan/"))) {
            logger.debug("Skipping JWT filter for public access endpoint: {} {}", method, path);
            return true;
        }

        if (method.equals("GET") &&
                (path.equals("/api/parking-lots") || path.startsWith("/api/parking-lots/"))) {
            logger.debug("Skipping JWT filter for public GET parking lots endpoint: {}", path);
            return true;
        }

        if (path.startsWith("/api/reservations")) {
            logger.debug("Skipping JWT filter for public reservation endpoint: {} {}", method, path);
            return true;
        }

        if (path.equals("/") ||
                path.equals("/error") ||
                path.equals("/swagger-ui.html") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/api-docs/") ||
                path.equals("/v3/api-docs.yaml") ||
                path.startsWith("/api-docs/") ||
                path.startsWith("/swagger-resources/") ||
                path.startsWith("/webjars/")) {
            logger.debug("Skipping JWT filter for Swagger/Error/Root: {}", path);
            return true;
        }

        logger.trace("Applying JWT filter for path: {} {}", method, path);
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String path = request.getServletPath();
            String method = request.getMethod();
            logger.trace("Processing JWT auth filter for: {} {}", method, path);

            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                logger.trace("JWT token found: {}...", jwt.substring(0, Math.min(10, jwt.length())));

                boolean isValid = tokenProvider.validateToken(jwt);
                logger.trace("Token validation result: {}", isValid);

                if (isValid) {
                    String userId = tokenProvider.getUserIdFromJWT(jwt);
                    logger.trace("User ID from token: {}", userId);

                    UserDetails userDetails = userDetailsService.loadUserById(userId);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("Authentication set in SecurityContext for user: {}", userDetails.getUsername());
                } else {
                    logger.debug("Invalid JWT token detected for path: {} {}", method, path);
                }
            } else {
                logger.trace("No JWT token found in request for path: {} {}", method, path);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}