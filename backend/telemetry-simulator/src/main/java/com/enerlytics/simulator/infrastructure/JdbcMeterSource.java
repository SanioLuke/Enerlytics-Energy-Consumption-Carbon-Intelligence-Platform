package com.enerlytics.simulator.infrastructure;

import com.enerlytics.simulator.domain.SimulatedMeter;
import com.enerlytics.simulator.domain.SimulationProfile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcMeterSource implements MeterSource {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMeterSource(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public List<SimulatedMeter> fetchActiveSimulatedMeters() {
        String sql = """
                SELECT m.id AS meter_id,
                       m.organization_id AS organization_id,
                       m.site_id AS site_id,
                       m.reading_interval_seconds AS reading_interval_seconds,
                       m.simulation_profile AS simulation_profile,
                       s.iana_timezone AS timezone
                FROM telemetry.meter m
                JOIN org.site s ON m.site_id = s.id
                WHERE m.simulated = TRUE
                  AND m.status = 'ACTIVE'
                  AND s.active = TRUE
                """;
        return jdbcTemplate.query(sql, new SimulatedMeterRowMapper());
    }

    private static class SimulatedMeterRowMapper implements RowMapper<SimulatedMeter> {
        @Override
        public SimulatedMeter mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SimulatedMeter(
                    UUID.fromString(rs.getString("meter_id")),
                    UUID.fromString(rs.getString("organization_id")),
                    UUID.fromString(rs.getString("site_id")),
                    rs.getInt("reading_interval_seconds"),
                    SimulationProfile.valueOf(rs.getString("simulation_profile")),
                    rs.getString("timezone")
            );
        }
    }
}
