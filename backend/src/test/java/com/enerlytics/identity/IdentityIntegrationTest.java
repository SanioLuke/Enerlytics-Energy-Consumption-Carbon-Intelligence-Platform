package com.enerlytics.identity;

import com.enerlytics.identity.api.dto.LoginRequest;
import com.enerlytics.identity.api.dto.RefreshRequest;
import com.enerlytics.identity.api.dto.TokenResponse;
import com.enerlytics.identity.application.RoleAssignmentService;
import com.enerlytics.identity.domain.UserEntity;
import com.enerlytics.identity.infrastructure.persistence.*;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.enerlytics.security.jwt.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdentityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        userOrganizationRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void validLoginReturnsTokens() {
        String password = "SecurePass123!";
        TestUser user = createUser("alice@example.com", "Alice", password, "VIEWER");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualToIgnoringCase("Bearer");
        assertThat(response.getBody().defaultOrganizationId()).isEqualTo(user.organization.getId());
    }

    @Test
    void invalidCredentialsReturn401() {
        TestUser user = createUser("bob@example.com", "Bob", "right-password", "VIEWER");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), "wrong-password"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disabledAccountCannotLogin() {
        String password = "SecurePass123!";
        TestUser user = createUser("carol@example.com", "Carol", password, "VIEWER");
        user.user.setStatus(com.enerlytics.identity.domain.UserStatus.SUSPENDED);
        userRepository.save(user.user);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredAccessTokenIsRejected() {
        TestUser user = createUser("dave@example.com", "Dave", "SecurePass123!", "VIEWER");

        String expiredToken = buildExpiredToken(user.user.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        headers.set("X-Organization-Id", user.organization.getId().toString());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/organizations/" + user.organization.getId(),
                HttpMethod.GET,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshFlowRotatesTokenAndRevokesOldOne() {
        String password = "SecurePass123!";
        TestUser user = createUser("eve@example.com", "Eve", password, "VIEWER");

        TokenResponse first = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                TokenResponse.class).getBody();

        ResponseEntity<TokenResponse> refreshResponse = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                new RefreshRequest(first.refreshToken()),
                TokenResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().refreshToken()).isNotEqualTo(first.refreshToken());

        ResponseEntity<Map> staleRefresh = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                new RefreshRequest(first.refreshToken()),
                Map.class);

        assertThat(staleRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesRefreshToken() {
        String password = "SecurePass123!";
        TestUser user = createUser("frank@example.com", "Frank", password, "VIEWER");

        TokenResponse tokens = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                TokenResponse.class).getBody();

        restTemplate.postForEntity("/api/v1/auth/logout", new com.enerlytics.identity.api.dto.LogoutRequest(tokens.refreshToken()), Void.class);

        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                "/api/v1/auth/refresh",
                new RefreshRequest(tokens.refreshToken()),
                Map.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userWithoutRequiredRoleIsDenied() {
        String password = "SecurePass123!";
        TestUser user = createUser("grace@example.com", "Grace", password);
        // No role assignment

        TokenResponse tokens = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                TokenResponse.class).getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        headers.set("X-Organization-Id", user.organization.getId().toString());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/organizations/" + user.organization.getId(),
                HttpMethod.GET,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crossTenantAccessIsDenied() {
        String password = "SecurePass123!";
        TestUser user = createUser("henry@example.com", "Henry", password, "VIEWER");
        OrganizationEntity otherOrg = organizationRepository.save(new OrganizationEntity("other-org", "Other Organization"));

        TokenResponse tokens = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                TokenResponse.class).getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        headers.set("X-Organization-Id", otherOrg.getId().toString());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/organizations/" + otherOrg.getId(),
                HttpMethod.GET,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void platformAdminCanAccessAnyOrganization() {
        String password = "SecurePass123!";
        TestUser user = createUser("admin@example.com", "Admin", password, "PLATFORM_ADMIN");
        OrganizationEntity otherOrg = organizationRepository.save(new OrganizationEntity("cross-org", "Cross Organization"));

        TokenResponse tokens = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new LoginRequest(user.user.getEmail(), password),
                TokenResponse.class).getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        headers.set("X-Organization-Id", otherOrg.getId().toString());
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/organizations/" + otherOrg.getId(),
                HttpMethod.GET,
                request,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TestUser createUser(String email, String displayName, String password, String roleCode) {
        OrganizationEntity organization = organizationRepository.save(
                new OrganizationEntity(UUID.randomUUID().toString().substring(0, 8), displayName + " Org"));
        UserEntity user = new UserEntity(email, displayName, passwordEncoder.encode(password));
        user.setExternalSubject(email.toLowerCase());
        user = userRepository.save(user);
        var membership = roleAssignmentService.ensureMembership(organization, user);
        roleAssignmentService.assignRole(organization, membership, roleCode);
        return new TestUser(user, organization);
    }

    private TestUser createUser(String email, String displayName, String password) {
        OrganizationEntity organization = organizationRepository.save(
                new OrganizationEntity(UUID.randomUUID().toString().substring(0, 8), displayName + " Org"));
        UserEntity user = new UserEntity(email, displayName, passwordEncoder.encode(password));
        user.setExternalSubject(email.toLowerCase());
        user = userRepository.save(user);
        roleAssignmentService.ensureMembership(organization, user);
        return new TestUser(user, organization);
    }

    private String buildExpiredToken(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.accessSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expiry = now.plus(1, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .claim("enabled", true)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    private record TestUser(UserEntity user, OrganizationEntity organization) {
    }
}
