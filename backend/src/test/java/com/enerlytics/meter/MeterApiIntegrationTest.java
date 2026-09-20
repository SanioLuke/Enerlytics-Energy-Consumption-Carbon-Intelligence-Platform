package com.enerlytics.meter;

import com.enerlytics.facility.domain.BuildingEntity;
import com.enerlytics.facility.domain.FloorAreaUnit;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.domain.ZoneEntity;
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
import com.enerlytics.meter.api.dto.*;
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.domain.MeterType;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeterApiIntegrationTest {

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
    private MeterRepository meterRepository;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        meterRepository.deleteAll();
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
    void meterLifecycle() {
        TestUser user = createUserWithRole("meter-life@example.com", "MeterLife", "FACILITY_MANAGER");
        String token = login(user.email, user.password);
        FacilityContext ctx = createFacility(user.org);

        CreateMeterRequest create = new CreateMeterRequest(
                "M-001", "Main Electricity Meter", "SN12345", "Schneider", "PM3250",
                MeterType.ELECTRICITY, null, 60, "kWh",
                ctx.site.getId(), ctx.building.getId(), ctx.zone.getId(), Map.of("phase", "3"));

        ResponseEntity<MeterResponse> createResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters", create, MeterResponse.class, token, user.org.getId());
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        MeterResponse meter = createResp.getBody();
        assertThat(meter.code()).isEqualTo("M-001");
        assertThat(meter.status()).isEqualTo(MeterStatus.PROVISIONING);

        ResponseEntity<MeterResponse> getResp = get(
                "/api/v1/organizations/" + user.org.getId() + "/meters/" + meter.id(), MeterResponse.class, token, user.org.getId());
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().metadata()).containsEntry("phase", "3");

        ResponseEntity<Map> listResp = get(
                "/api/v1/organizations/" + user.org.getId() + "/meters?status=PROVISIONING", Map.class, token, user.org.getId());
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().get("totalElements")).isEqualTo(1);

        ResponseEntity<MeterResponse> activateResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters/" + meter.id() + "/commission",
                null, MeterResponse.class, token, user.org.getId());
        assertThat(activateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activateResp.getBody().status()).isEqualTo(MeterStatus.ACTIVE);
        assertThat(activateResp.getBody().commissionedAt()).isNotNull();

        ResponseEntity<MeterResponse> deactivateResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters/" + meter.id() + "/deactivate",
                null, MeterResponse.class, token, user.org.getId());
        assertThat(deactivateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deactivateResp.getBody().status()).isEqualTo(MeterStatus.OFFLINE);

        ResponseEntity<MeterResponse> hbResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters/" + meter.id() + "/heartbeat",
                null, MeterResponse.class, token, user.org.getId());
        assertThat(hbResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hbResp.getBody().lastSeenAt()).isNotNull();

        BuildingEntity otherBuilding = buildingRepository.save(
                new BuildingEntity(user.org, ctx.site, "BLDG-B", "Building B"));
        ZoneEntity otherZone = zoneRepository.save(
                new ZoneEntity(user.org, ctx.site, otherBuilding, "ZONE-B", "Zone B"));
        AssignLocationRequest assign = new AssignLocationRequest(ctx.site.getId(), otherBuilding.getId(), otherZone.getId());
        ResponseEntity<MeterResponse> locResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters/" + meter.id() + "/location",
                assign, MeterResponse.class, token, user.org.getId());
        assertThat(locResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(locResp.getBody().zoneId()).isEqualTo(otherZone.getId());

        ResponseEntity<MeterResponse> decomResp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters/" + meter.id() + "/decommission",
                null, MeterResponse.class, token, user.org.getId());
        assertThat(decomResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decomResp.getBody().status()).isEqualTo(MeterStatus.DECOMMISSIONED);
        assertThat(decomResp.getBody().decommissionedAt()).isNotNull();
    }

    @Test
    void searchMetersByNameOrSerial() {
        TestUser user = createUserWithRole("meter-search@example.com", "MeterSearch", "FACILITY_MANAGER");
        String token = login(user.email, user.password);
        FacilityContext ctx = createFacility(user.org);
        MeterEntity alpha = new MeterEntity(user.org, ctx.site, "M-A", "Alpha Meter", 60);
        alpha.setSerialNumber("SN-ALPHA");
        alpha.setMeterType(MeterType.ELECTRICITY);
        MeterEntity beta = new MeterEntity(user.org, ctx.site, "M-B", "Beta Meter", 300);
        beta.setMeterType(MeterType.ELECTRICITY);
        meterRepository.save(alpha);
        meterRepository.save(beta);

        ResponseEntity<Map> searchName = get(
                "/api/v1/organizations/" + user.org.getId() + "/meters?search=Alpha", Map.class, token, user.org.getId());
        assertThat(searchName.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchName.getBody().get("totalElements")).isEqualTo(1);

        ResponseEntity<Map> searchSerial = get(
                "/api/v1/organizations/" + user.org.getId() + "/meters?search=SN-ALPHA", Map.class, token, user.org.getId());
        assertThat(searchSerial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchSerial.getBody().get("totalElements")).isEqualTo(1);
    }

    @Test
    void viewerCannotCreateMeter() {
        TestUser viewer = createUserWithRole("viewer-meter@example.com", "ViewerMeter", "VIEWER");
        String token = login(viewer.email, viewer.password);

        CreateMeterRequest create = new CreateMeterRequest(
                "M-002", "Viewer Test", null, null, null, MeterType.ELECTRICITY,
                null, 60, "kWh", UUID.randomUUID(), null, null, null);

        ResponseEntity<Map> resp = post(
                "/api/v1/organizations/" + viewer.org.getId() + "/meters", create, Map.class, token, viewer.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidReadingIntervalReturnsBadRequest() {
        TestUser user = createUserWithRole("meter-interval@example.com", "MeterInterval", "FACILITY_MANAGER");
        String token = login(user.email, user.password);
        FacilityContext ctx = createFacility(user.org);

        CreateMeterRequest create = new CreateMeterRequest(
                "M-003", "Bad Interval", null, null, null, MeterType.ELECTRICITY,
                null, 0, "kWh", ctx.site.getId(), null, null, null);

        ResponseEntity<Map> resp = post(
                "/api/v1/organizations/" + user.org.getId() + "/meters", create, Map.class, token, user.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private FacilityContext createFacility(OrganizationEntity org) {
        SiteEntity site = siteRepository.save(new SiteEntity(org, "S-METER", "Meter Site", "UTC"));
        BuildingEntity building = buildingRepository.save(new BuildingEntity(org, site, "BLDG-M", "Meter Building"));
        ZoneEntity zone = zoneRepository.save(new ZoneEntity(org, site, building, "ZONE-M", "Meter Zone"));
        return new FacilityContext(site, building, zone);
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
            String payload = body == null ? "" : objectMapper.writeValueAsString(body);
            return restTemplate.postForEntity(path, new HttpEntity<>(payload.isEmpty() ? null : payload, headers), type);
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

    private record TestUser(UserEntity user, OrganizationEntity org, String email, String password) {
    }

    private record FacilityContext(SiteEntity site, BuildingEntity building, ZoneEntity zone) {
    }
}
