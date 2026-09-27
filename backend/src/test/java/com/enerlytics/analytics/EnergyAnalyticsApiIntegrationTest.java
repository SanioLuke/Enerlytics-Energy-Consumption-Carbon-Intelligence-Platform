package com.enerlytics.analytics;

import com.enerlytics.analytics.application.EnergyAggregationService;
import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.facility.domain.SiteEntity;
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
import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import com.enerlytics.meter.infrastructure.persistence.MeterRepository;
import com.enerlytics.organization.domain.OrganizationEntity;
import com.enerlytics.organization.infrastructure.persistence.OrganizationRepository;
import com.enerlytics.telemetry.domain.MeterReadingEntity;
import com.enerlytics.telemetry.infrastructure.persistence.MeterReadingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EnergyAnalyticsApiIntegrationTest {

    private static final Instant HOUR_START = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EnergyAggregationService aggregationService;

    @Autowired
    private EnergyAggregateRepository aggregateRepository;

    @Autowired
    private MeterReadingRepository readingRepository;

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

    @AfterEach
    void cleanUp() {
        aggregateRepository.deleteAll();
        readingRepository.deleteAll();
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
    void returnsHourlySeriesForMeter() {
        TestUser user = createUserWithRole("analyst@example.com", "Analyst", "ENERGY_ANALYST");
        String token = login(user.email, user.password);
        SiteEntity site = siteRepository.save(new SiteEntity(user.org, "S-AN", "Analytics Site", "UTC"));
        MeterEntity meter = new MeterEntity(user.org, site, "M-AN", "Analytics Meter", 60);
        meter.setStatus(MeterStatus.ACTIVE);
        meter = meterRepository.save(meter);

        insertReading(user.org.getId(), meter.getId(), HOUR_START.plusSeconds(60), "1.5", "30", "0.95");
        insertReading(user.org.getId(), meter.getId(), HOUR_START.plusSeconds(120), "2.5", "50", "0.85");
        aggregationService.aggregateReading(user.org.getId(), meter.getId(), HOUR_START.plusSeconds(60));
        aggregationService.aggregateReading(user.org.getId(), meter.getId(), HOUR_START.plusSeconds(120));

        String url = "/api/v1/organizations/" + user.org.getId()
                + "/analytics/energy?dimension=METER&dimensionId=" + meter.getId()
                + "&granularity=HOUR&from=2026-01-15T00:00:00Z&to=2026-01-16T00:00:00Z";
        ResponseEntity<List> resp = get(url, List.class, token, user.org.getId());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = resp.getBody();
        assertThat(body).hasSize(1);
        Map<String, Object> bucket = (Map<String, Object>) body.get(0);
        assertThat(((Number) bucket.get("energyConsumedKwh")).doubleValue()).isEqualTo(4.0);
        assertThat(((Number) bucket.get("averagePowerKw")).doubleValue()).isEqualTo(40.0);
        assertThat(((Number) bucket.get("peakPowerKw")).doubleValue()).isEqualTo(50.0);
        assertThat(((Number) bucket.get("readingCount")).longValue()).isEqualTo(2);
        assertThat(((Number) bucket.get("estimatedReadingCount")).longValue()).isEqualTo(60);
    }

    @Test
    void organizationDimensionDefaultsToRequestedOrg() {
        TestUser user = createUserWithRole("analyst2@example.com", "Analyst2", "ENERGY_ANALYST");
        String token = login(user.email, user.password);
        SiteEntity site = siteRepository.save(new SiteEntity(user.org, "S-OD", "Org Dim Site", "UTC"));
        MeterEntity meter = new MeterEntity(user.org, site, "M-OD", "Org Dim Meter", 60);
        meter.setStatus(MeterStatus.ACTIVE);
        meter = meterRepository.save(meter);

        insertReading(user.org.getId(), meter.getId(), HOUR_START.plusSeconds(60), "7.5", "75", "0.90");
        aggregationService.aggregateReading(user.org.getId(), meter.getId(), HOUR_START.plusSeconds(60));

        String url = "/api/v1/organizations/" + user.org.getId()
                + "/analytics/energy?granularity=HOUR&from=2026-01-15T00:00:00Z&to=2026-01-16T00:00:00Z";
        ResponseEntity<List> resp = get(url, List.class, token, user.org.getId());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        Map<String, Object> bucket = (Map<String, Object>) resp.getBody().get(0);
        assertThat(((Number) bucket.get("energyConsumedKwh")).doubleValue()).isEqualTo(7.5);
    }

    @Test
    void meterFromAnotherTenantIsNotFound() {
        TestUser user = createUserWithRole("analyst3@example.com", "Analyst3", "ENERGY_ANALYST");
        TestUser other = createUserWithRole("other@example.com", "Other", "ENERGY_ANALYST");
        String token = login(user.email, user.password);
        SiteEntity site = siteRepository.save(new SiteEntity(other.org, "S-X", "Other Site", "UTC"));
        MeterEntity foreign = new MeterEntity(other.org, site, "M-X", "Foreign", 60);
        foreign.setStatus(MeterStatus.ACTIVE);
        foreign = meterRepository.save(foreign);

        String url = "/api/v1/organizations/" + user.org.getId()
                + "/analytics/energy?dimension=METER&dimensionId=" + foreign.getId()
                + "&granularity=HOUR&from=2026-01-15T00:00:00Z&to=2026-01-16T00:00:00Z";
        ResponseEntity<Map> resp = get(url, Map.class, token, user.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missingDimensionIdIsBadRequest() {
        TestUser user = createUserWithRole("analyst4@example.com", "Analyst4", "ENERGY_ANALYST");
        String token = login(user.email, user.password);

        String url = "/api/v1/organizations/" + user.org.getId()
                + "/analytics/energy?dimension=METER&granularity=HOUR"
                + "&from=2026-01-15T00:00:00Z&to=2026-01-16T00:00:00Z";
        ResponseEntity<Map> resp = get(url, Map.class, token, user.org.getId());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void insertReading(UUID orgId, UUID meterId, Instant timestamp, String energyKwh, String powerKw, String pf) {
        MeterReadingEntity reading = new MeterReadingEntity(orgId, meterId, timestamp,
                new BigDecimal(energyKwh), UUID.randomUUID());
        reading.setPowerKw(new BigDecimal(powerKw));
        reading.setPowerFactor(new BigDecimal(pf));
        reading.setQualityStatus("OK");
        readingRepository.save(reading);
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

    private <T> ResponseEntity<T> get(String path, Class<T> type, String token, UUID orgId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Organization-Id", orgId.toString());
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    private record TestUser(UserEntity user, OrganizationEntity org, String email, String password) {
    }
}
