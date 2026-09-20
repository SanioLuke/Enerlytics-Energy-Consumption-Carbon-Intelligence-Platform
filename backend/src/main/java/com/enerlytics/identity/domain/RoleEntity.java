package com.enerlytics.identity.domain;

import com.enerlytics.common.domain.AuditedEntity;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "iam", name = "role")
public class RoleEntity extends AuditedEntity {

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_scope", nullable = false, length = 24)
    private RoleScope roleScope = RoleScope.ORGANIZATION;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "organization_id", columnDefinition = "uuid")
    private UUID organizationId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            schema = "iam",
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();

    protected RoleEntity() {
    }

    public RoleEntity(String code, String name, RoleScope roleScope) {
        this.code = code;
        this.name = name;
        this.roleScope = roleScope;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RoleScope getRoleScope() {
        return roleScope;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Set<PermissionEntity> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<PermissionEntity> permissions) {
        this.permissions = permissions;
    }
}
