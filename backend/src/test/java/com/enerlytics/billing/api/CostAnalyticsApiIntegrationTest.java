package com.enerlytics.billing.api;

import com.enerlytics.analytics.domain.AggregationGranularity;
import com.enerlytics.analytics.domain.DimensionType;
import com.enerlytics.analytics.domain.EnergyAggregateEntity;
import com.enerlytics.analytics.infrastructure.persistence.EnergyAggregateRepository;
import com.enerlytics.billing.domain.TariffDayType;
import com.enerlytics.billing.domain.TariffEntity;
import com.enerlytics.billing.domain.TariffRateEntity;
import com.enerlytics.billing.domain.TariffType;
import com.enerlytics.billing.infrastructure.persistence.EnergyCostRepository;
import com.enerlytics.billing.infrastructure.persistence.TariffRepository;
import com.enerlytics.facility.domain.SiteEntity;
import com.enerlytics.facility.infrastructure.persistence.SiteRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CostAnalyticsApiIntegrationTest {

    private static final Instant HOUR = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EnergyAggregateRepository energyAggregateRepository;

    @Autowired
    private EnergyCostRepository costRepository;

    @Autowired
    private TariffRepository tariffRepository;

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
    private MeterRepository meterRepository;

    @Autowired
    private RoleAssignmentService roleAssignmentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        costRepository.deleteAll();
        tariffRepository.deleteAll();
        energyAggregateRepository.deleteAll();
        meterRepository.deleteAll();
        siteRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        userOrganizationRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void dailyCostEndpointReturnsComputedCost() {
        TestUser user = createUserWithRole("cost@example.com", "Cost Analyst", "ENERGY_ANALYST");
        String token = login(user.email, user.password);
        SiteEntity site = siteRepository.save(new SiteEntity(user.org, "S-B", "Billing Site", "UTC"));
        MeterEntity meter = new MeterEntity(user.org, site, "M-B", "Billing Meter", 60);
        meter.setStatus(MeterStatus.ACTIVE);
        meter = meterRepository.save(meter);

        insertAggregate(user.org.getId(), meter.getId(), HOUR, new BigDecimal("10.000"));
        TariffEntity tariff = new TariffEntity(user.org, site, "Flat", TariffType.FLAT_RATE,
                "USD", "UTC", LocalDate.parse("2026-01-01"), null);
        tariff.addRate(new TariffRateEntity(TariffDayType.ALL, LocalTime.parse("00:00"),
                LocalTime.parse("23:59"), new BigDecimal("0.20"), null, 0));
        tariffRepository.save(tariff);

        String url = "/api/v1/organizations/" + user.org.getId()
                + "/billing/costs/daily?dimension=METER&dimensionId=" + meter.getId() + "&date=2026-01-15";
        ResponseEntity<List> resp = get(url, List.class, token, user.org.getId());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = resp.getBody();
        assertThat(body).hasSize(1);
        Map<String, Object> row = body.get(0);
        assertThat(((Number) row.get("energyConsumedKwh")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) row.get("totalCost")).doubleValue()).isEqualTo(2.0);
        assertThat(row.get("currency")).isEqualTo("USD");
        assertThat(row.get("qualityStatus")).isEqualTo("COMPLETE");
    }

    private void insertAggregate(UUID orgId, UUID meterId, Instant start, BigDecimal energy) {
        EnergyAggregateEntity agg = new EnergyAggregateEntity(orgId, DimensionType.METER, meterId,
                AggregationGranularity.HOUR, start, start.plusSeconds(3600));
        agg.applyMetrics(energy, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, 1L, 0L,
                BigDecimal.valueOf(100), Instant.now());
        energyAggregateRepository.save(agg);
    }

    private TestUser createUserWithRole(String email, String displayName, String roleCode) {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity(UUID.randomUUID().toString().substring(0, 8), displayName + " Org"));
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
