package com.enerlytics.security.jwt;

import com.enerlytics.identity.domain.UserEntity;
import com.enerlytics.identity.infrastructure.persistence.UserRepository;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.tenant.TenantAuthorizationService;
import com.enerlytics.security.tenant.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   UserRepository userRepository,
                                   TenantAuthorizationService tenantAuthorizationService) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token)) {
                JwtTokenProvider.AccessTokenClaims claims = tokenProvider.parseAccessToken(token);
                UserEntity user = userRepository.findById(claims.userId())
                        .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
                if (!user.isActive()) {
                    throw new DisabledException("Account is disabled");
                }

                UUID organizationId = resolveOrganizationId(request, claims.defaultOrganizationId());

                TenantAuthorizationService.TenantAccess access;
                if (organizationId != null) {
                    access = tenantAuthorizationService.resolve(user.getId(), organizationId);
                } else {
                    access = new TenantAuthorizationService.TenantAccess(null, false, false, Collections.emptySet());
                }

                TenantContext.setCurrentOrganizationId(access.organizationId());

                UserPrincipal principal = new UserPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.isActive(),
                        access.organizationId(),
                        access.platformAdmin(),
                        access.authorities()
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(request);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        } catch (BadCredentialsException | DisabledException ex) {
            SecurityContextHolder.clearContext();
            throw ex;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private UUID resolveOrganizationId(HttpServletRequest request, UUID defaultOrganizationId) {
        String header = request.getHeader(ORGANIZATION_HEADER);
        if (StringUtils.hasText(header)) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException ex) {
                return defaultOrganizationId;
            }
        }
        return defaultOrganizationId;
    }
}
