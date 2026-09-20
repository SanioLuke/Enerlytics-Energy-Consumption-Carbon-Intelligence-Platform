package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds the MVP roles and permissions.
 *
 * <p>This migration is intentionally idempotent: it checks for existing rows
 * before inserting. Seeded role-to-permission mappings must not be removed at
 * runtime; changes require a reviewed migration.</p>
 */
public class V1_0_1__SeedRolesAndPermissions extends BaseJavaMigration {

    private static final String[] PERMISSIONS = {
            "organization:read", "organization:write",
            "user:read", "user:write",
            "site:read", "site:write",
            "meter:read", "meter:write",
            "telemetry:read", "telemetry:write",
            "energy:read",
            "carbon:read",
            "tariff:read", "tariff:write",
            "analytics:read",
            "forecast:read",
            "alert:read", "alert:write",
            "report:read", "report:write",
            "notification:read", "notification:write",
            "audit:read"
    };

    private static final Map<String, RoleDefinition> ROLES = new LinkedHashMap<>();

    static {
        ROLES.put("PLATFORM_ADMIN", new RoleDefinition("Platform Administrator",
                "Full platform and cross-tenant administrative access.", "PLATFORM"));
        ROLES.put("ORGANIZATION_ADMIN", new RoleDefinition("Organization Administrator",
                "Manages one organization and its users, sites, meters, and configuration.", "ORGANIZATION"));
        ROLES.put("FACILITY_MANAGER", new RoleDefinition("Facility Manager",
                "Oversee sites, meters, telemetry, and alerts.", "ORGANIZATION"));
        ROLES.put("ENERGY_ANALYST", new RoleDefinition("Energy Analyst",
                "Reads energy, carbon, analytics, forecasts, and telemetry.", "ORGANIZATION"));
        ROLES.put("SUSTAINABILITY_MANAGER", new RoleDefinition("Sustainability Manager",
                "Reads carbon, sustainability, analytics, and reports.", "ORGANIZATION"));
        ROLES.put("VIEWER", new RoleDefinition("Viewer",
                "Read-only access to organization data and reports.", "ORGANIZATION"));
    }

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new LinkedHashMap<>();

    static {
        ROLE_PERMISSIONS.put("PLATFORM_ADMIN", Set.of(PERMISSIONS));
        ROLE_PERMISSIONS.put("ORGANIZATION_ADMIN", Set.of(
                "organization:read", "organization:write",
                "user:read", "user:write",
                "site:read", "site:write",
                "meter:read", "meter:write",
                "telemetry:read", "telemetry:write",
                "energy:read", "carbon:read",
                "tariff:read", "tariff:write",
                "analytics:read", "forecast:read",
                "alert:read", "alert:write",
                "report:read", "report:write",
                "notification:read", "notification:write",
                "audit:read"));
        ROLE_PERMISSIONS.put("FACILITY_MANAGER", Set.of(
                "organization:read", "site:read", "site:write",
                "meter:read", "meter:write", "telemetry:read", "telemetry:write",
                "alert:read", "alert:write",
                "energy:read"));
        ROLE_PERMISSIONS.put("ENERGY_ANALYST", Set.of(
                "organization:read", "site:read", "meter:read",
                "telemetry:read", "energy:read", "carbon:read", "analytics:read",
                "forecast:read", "report:read"));
        ROLE_PERMISSIONS.put("SUSTAINABILITY_MANAGER", Set.of(
                "organization:read", "site:read", "meter:read",
                "carbon:read", "analytics:read", "forecast:read",
                "report:read", "report:write"));
        ROLE_PERMISSIONS.put("VIEWER", Set.of(
                "organization:read", "site:read", "meter:read",
                "energy:read", "carbon:read", "analytics:read", "report:read"));
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        for (String code : PERMISSIONS) {
            ensurePermission(connection, code);
        }
        for (Map.Entry<String, RoleDefinition> entry : ROLES.entrySet()) {
            ensureRole(connection, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Set<String>> entry : ROLE_PERMISSIONS.entrySet()) {
            UUID roleId = roleId(connection, entry.getKey());
            if (roleId == null) {
                throw new IllegalStateException("Missing role: " + entry.getKey());
            }
            for (String permissionCode : entry.getValue()) {
                UUID permissionId = permissionId(connection, permissionCode);
                if (permissionId == null) {
                    throw new IllegalStateException("Missing permission: " + permissionCode);
                }
                ensureRolePermission(connection, roleId, permissionId);
            }
        }
    }

    private void ensurePermission(Connection connection, String code) throws Exception {
        if (permissionId(connection, code) != null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO iam.permission (id, code, description) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, code);
            ps.setString(3, "Permission to perform " + code);
            ps.executeUpdate();
        }
    }

    private UUID permissionId(Connection connection, String code) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM iam.permission WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("id"));
                }
            }
        }
        return null;
    }

    private void ensureRole(Connection connection, String code, RoleDefinition def) throws Exception {
        if (roleId(connection, code) != null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO iam.role (id, code, name, description, role_scope, system_role, active) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, code);
            ps.setString(3, def.name);
            ps.setString(4, def.description);
            ps.setString(5, def.scope);
            ps.setBoolean(6, true);
            ps.setBoolean(7, true);
            ps.executeUpdate();
        }
    }

    private UUID roleId(Connection connection, String code) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM iam.role WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("id"));
                }
            }
        }
        return null;
    }

    private void ensureRolePermission(Connection connection, UUID roleId, UUID permissionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM iam.role_permission WHERE role_id = ? AND permission_id = ?")) {
            ps.setObject(1, roleId);
            ps.setObject(2, permissionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO iam.role_permission (role_id, permission_id, created_at) VALUES (?, ?, now())")) {
            ps.setObject(1, roleId);
            ps.setObject(2, permissionId);
            ps.executeUpdate();
        }
    }

    private record RoleDefinition(String name, String description, String scope) {}
}
