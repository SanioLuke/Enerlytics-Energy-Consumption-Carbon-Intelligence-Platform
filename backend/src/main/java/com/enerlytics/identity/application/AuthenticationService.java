package com.enerlytics.identity.application;

import com.enerlytics.identity.api.dto.*;
import com.enerlytics.identity.domain.*;
import com.enerlytics.identity.infrastructure.persistence.*;
import com.enerlytics.security.UserPrincipal;
import com.enerlytics.security.jwt.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthenticationService(AuthenticationManager authenticationManager,
                               UserRepository userRepository,
                               UserOrganizationRepository userOrganizationRepository,
                               RoleAssignmentRepository roleAssignmentRepository,
                               RefreshTokenService refreshTokenService,
                               JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserEntity user = userRepository.findByNormalizedEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        user.recordSignIn();
        userRepository.save(user);

        UUID defaultOrganizationId = resolveDefaultOrganizationId(user);
        String accessToken = jwtTokenProvider.generateAccessToken(user, defaultOrganizationId);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new TokenResponse(accessToken, refreshToken, "Bearer",
                jwtTokenProvider.properties().accessExpirationMs(), defaultOrganizationId);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshTokenEntity oldToken = refreshTokenService.findValid(request.refreshToken());
        UserEntity user = oldToken.getUser();

        String newRefreshTokenValue = refreshTokenService.createRefreshToken(user);
        refreshTokenService.rotate(oldToken, newRefreshTokenValue);

        UUID defaultOrganizationId = resolveDefaultOrganizationId(user);
        String accessToken = jwtTokenProvider.generateAccessToken(user, defaultOrganizationId);

        return new TokenResponse(accessToken, newRefreshTokenValue, "Bearer",
                jwtTokenProvider.properties().accessExpirationMs(), defaultOrganizationId);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public UserInfoResponse me() {
        UserPrincipal principal = currentPrincipal();
        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        List<UserOrganizationEntity> memberships = userOrganizationRepository.findByUserId(user.getId());
        List<UserInfoResponse.OrganizationMembershipResponse> orgs = new ArrayList<>();
        for (UserOrganizationEntity membership : memberships) {
            if (!membership.isActive()) {
                continue;
            }
            List<RoleAssignmentEntity> assignments = roleAssignmentRepository.findByUserOrganizationId(membership.getId());
            List<String> roles = new ArrayList<>();
            Set<String> permissions = new LinkedHashSet<>();
            for (RoleAssignmentEntity assignment : assignments) {
                if (assignment.isActiveAt(java.time.Instant.now())) {
                    roles.add(assignment.getRole().getCode());
                    for (PermissionEntity permission : assignment.getRole().getPermissions()) {
                        permissions.add(permission.getCode());
                    }
                }
            }
            orgs.add(new UserInfoResponse.OrganizationMembershipResponse(
                    membership.getOrganization().getId(),
                    membership.getOrganization().getOrganizationKey(),
                    membership.getOrganization().getDisplayName(),
                    roles,
                    List.copyOf(permissions)
            ));
        }
        return new UserInfoResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(), orgs);
    }

    private UUID resolveDefaultOrganizationId(UserEntity user) {
        return userOrganizationRepository.findByUserId(user.getId()).stream()
                .filter(UserOrganizationEntity::isActive)
                .findFirst()
                .map(m -> m.getOrganization().getId())
                .orElse(null);
    }

    private UserPrincipal currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal)) {
            throw new IllegalStateException("No authenticated user");
        }
        return (UserPrincipal) principal;
    }
}
