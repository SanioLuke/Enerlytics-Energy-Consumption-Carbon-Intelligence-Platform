package com.enerlytics.meter.infrastructure.persistence;

import com.enerlytics.meter.domain.MeterEntity;
import com.enerlytics.meter.domain.MeterStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MeterSpecification {

    private MeterSpecification() {
    }

    public static Specification<MeterEntity> byOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    public static Specification<MeterEntity> bySite(UUID siteId) {
        return (root, query, cb) -> cb.equal(root.get("site").get("id"), siteId);
    }

    public static Specification<MeterEntity> byBuilding(UUID buildingId) {
        return (root, query, cb) -> cb.equal(root.get("building").get("id"), buildingId);
    }

    public static Specification<MeterEntity> byZone(UUID zoneId) {
        return (root, query, cb) -> cb.equal(root.get("zone").get("id"), zoneId);
    }

    public static Specification<MeterEntity> byStatus(MeterStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<MeterEntity> search(String query) {
        return (root, cq, cb) -> {
            String pattern = "%" + query.toLowerCase() + "%";
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("meterName")), pattern));
            predicates.add(cb.like(cb.lower(root.get("meterCode")), pattern));
            predicates.add(cb.like(cb.lower(cb.coalesce(root.get("serialNumber"), "")), pattern));
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
