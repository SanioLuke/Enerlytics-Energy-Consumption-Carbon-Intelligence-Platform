package com.enerlytics.facility;

import com.enerlytics.facility.api.dto.*;
import com.enerlytics.facility.domain.FloorAreaUnit;
import com.enerlytics.facility.infrastructure.persistence.BuildingRepository;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
import com.enerlytics.facility.infrastructure.persistence.ZoneRepository;
import com.enerlytics.identity.api.dto.LoginRequest;
import com.enerlytics.identity.api.dto.TokenResponse;
import com.enerlytics.identity.application.RoleAssignmentService;
import com.enerlytics.identity.domain.MembershipStatus;
import com.enerlytics.identity.domain.UserEntity;
import com.enerlytics.identity.domain.UserOrganizationEntity;
import com.enerlytics.identity.infrastructure.persistence.*;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FacilityApiIntegrationTest {

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
    private SiteRepository siteRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        zoneRepository.deleteAll();
        buildingRepository.deleteAll();
        siteRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        userOrganizationRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void siteCrudLifecycle() {
        TestUser user = createUserWithRole("site-crud@example.com", "SiteCrud", "ORGANIZATION_ADMIN");
        String token = login(user.email, user.password);

        CreateSiteRequest create = new CreateSiteRequest(
                "HQ", "Headquarters", "Main office", "1 Energy Way", "US", "CA", "San Francisco", "94105",
                new BigDecimal("37.7749"), new BigDecimal("-122.4194"), "America/Los_Angeles", null, "USD",
                new BigDecimal("50000"), FloorAreaUnit.FT2, null, null);

        ResponseEntity<SiteResponse> createResp = post("/api/v1/organizations/" + user.org.getId() + "/sites", create, SiteResponse.class, token, user.org.getId());
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getBody()).isNotNull();
        SiteResponse site = createResp.getBody();
        assertThat(site.code()).isEqualTo("HQ");
        assertThat(site.currency()).isEqualTo("USD");

        ResponseEntity<SiteResponse> getResp = get("/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id(), SiteResponse.class, token, user.org.getId());
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().name()).isEqualTo("Headquarters");

        UpdateSiteRequest update = new UpdateSiteRequest("HQ Updated", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        ResponseEntity<SiteResponse> putResp = put("/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id(), update, SiteResponse.class, token, user.org.getId());
        assertThat(putResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResp.getBody().name()).isEqualTo("HQ Updated");

        ResponseEntity<Void> deleteResp = delete("/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id(), token, user.org.getId());
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> missing = get("/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id(), Map.class, token, user.org.getId());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sitePaginationSortingAndSearch() {
        TestUser user = createUserWithRole("site-page@example.com", "SitePage", "ORGANIZATION_ADMIN");
        String token = login(user.email, user.password);
        createSite(user, "A", "Alpha Site");
        createSite(user, "B", "Beta Site");
        createSite(user, "C", "Gamma Site");

        ResponseEntity<Map> listResp = get("/api/v1/organizations/" + user.org.getId() + "/sites?page=0&size=2&sort=name,desc", Map.class, token, user.org.getId());
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().get("totalElements")).isEqualTo(3);
        assertThat(listResp.getBody().get("content")).asList().hasSize(2);

        ResponseEntity<Map> searchResp = get("/api/v1/organizations/" + user.org.getId() + "/sites?search=Alpha", Map.class, token, user.org.getId());
        assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResp.getBody().get("totalElements")).isEqualTo(1);
    }

    @Test
    void buildingAndZoneCrud() {
        TestUser user = createUserWithRole("building-crud@example.com", "BuildingCrud", "ORGANIZATION_ADMIN");
        String token = login(user.email, user.password);
        SiteResponse site = createSite(user, "SITE-01", "Industrial Park");

        CreateBuildingRequest buildingReq = new CreateBuildingRequest(
                "BLDG-A", "Assembly Hall", "Main assembly building",
                new BigDecimal("12000"), FloorAreaUnit.M2, "INDUSTRIAL", null);
        ResponseEntity<BuildingResponse> buildingResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id() + "/buildings",
                buildingReq, BuildingResponse.class, token, user.org.getId());
        assertThat(buildingResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BuildingResponse building = buildingResp.getBody();
        assertThat(building.code()).isEqualTo("BLDG-A");

        CreateZoneRequest zoneReq = new CreateZoneRequest("ZONE-1", "Assembly Floor", null, "PRODUCTION", building.id());
        ResponseEntity<ZoneResponse> zoneResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id() + "/buildings/" + building.id() + "/zones",
                zoneReq, ZoneResponse.class, token, user.org.getId());
        assertThat(zoneResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ZoneResponse zone = zoneResp.getBody();
        assertThat(zone.buildingId()).isEqualTo(building.id());

        ResponseEntity<Void> zoneDelete = delete(
                "/api/v1/organizations/" + user.org.getId() + "/sites/" + site.id() + "/buildings/" + building.id() + "/zones/" + zone.id(),
                token, user.org.getId());
        assertThat(zoneDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void unauthorizedRoleCannotCreateSite() {
        TestUser viewer = createUserWithRole("viewer-site@example.com", "ViewerSite", "VIEWER");
        String token = login(viewer.email, viewer.password);

        CreateSiteRequest create = new CreateSiteRequest(
                "HQ", "Headquarters", null, null, null, null, null, null,
                null, null, "UTC", null, "USD", null, FloorAreaUnit.M2, null, null);

        ResponseEntity<Map> resp = post("/api/v1/organizations/" + viewer.org.getId() + "/sites", create, Map.class, token, viewer.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crossTenantAccessToSiteIsDenied() {
        TestUser user = createUserWithRole("tenant-a@example.com", "TenantA", "ORGANIZATION_ADMIN");
        OrganizationEntity otherOrg = organizationRepository.save(new OrganizationEntity("other-facility-org", "Other Facility Org"));
        String token = login(user.email, user.password);

        ResponseEntity<Map> resp = get("/api/v1/organizations/" + otherOrg.getId() + "/sites", Map.class, token, otherOrg.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidTimezoneReturnsValidationError() {
        TestUser user = createUserWithRole("invalid-timezone@example.com", "InvalidTZ", "ORGANIZATION_ADMIN");
        String token = login(user.email, user.password);

        CreateSiteRequest create = new CreateSiteRequest(
                "HQ", "Headquarters", null, null, null, null, null, null,
                null, null, "Mars/Colony", null, "USD", null, FloorAreaUnit.M2, null, null);

        ResponseEntity<Map> resp = post("/api/v1/organizations/" + user.org.getId() + "/sites", create, Map.class, token, user.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private SiteResponse createSite(TestUser user, String code, String name) {
        String token = login(user.email, user.password);
        CreateSiteRequest create = new CreateSiteRequest(
                code, name, null, null, null, null, null, null,
                null, null, "UTC", null, "USD", null, FloorAreaUnit.M2, null, null);
        ResponseEntity<SiteResponse> resp = post("/api/v1/organizations/" + user.org.getId() + "/sites", create, SiteResponse.class, token, user.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private TestUser createUserWithRole(String email, String displayName, String roleCode) {
        OrganizationEntity org = organizationRepository.save(
                new OrganizationEntity(UUID.randomUUID().toString().substring(0, 8), displayName + " Org"));
        String password = "SecurePass123!";
        UserEntity user = new UserEntity(email, displayName, passwordEncoder.encode(password));
        user.setExternalSubject(email.toLowerCase());
        user = userRepository.save(user);
        UserOrganizationEntity membership = new UserOrganizationEntity(org, user);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        membership = userOrganizationRepository.save(membership);
        roleAssignmentService.assignRole(org, membership, roleCode);
        return new TestUser(user, org, email, password);
    }

    private String login(String email, String password) {
        ResponseEntity<TokenResponse> resp = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, password), TokenResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().accessToken();
    }

    private <T> ResponseEntity<T> post(String path, Object body, Class<T> type, String token, UUID orgId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("X-Organization-Id", orgId.toString());
        try {
            return restTemplate.postForEntity(path, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> ResponseEntity<T> get(String path, Class<T> type, String token, UUID orgId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Organization-Id", orgId.toString());
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    private <T> ResponseEntity<T> put(String path, Object body, Class<T> type, String token, UUID orgId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("X-Organization-Id", orgId.toString());
        try {
            return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<Void> delete(String path, String token, UUID orgId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Organization-Id", orgId.toString());
        return restTemplate.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }

    private record TestUser(UserEntity user, OrganizationEntity org, String email, String password) {
    }
}
