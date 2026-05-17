package com.sael.domain.repository;
import com.sael.domain.entity.AlertRule;
import com.sael.domain.enums.SensorType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AlertRuleRepository extends JpaRepository<AlertRule,UUID> {
    List<AlertRule> findByTenantIdAndScopeIdAndIsActiveTrue(UUID tenantId, UUID scopeId);
    @Query("SELECT r FROM AlertRule r WHERE r.tenantId=:tid AND r.isActive=true AND (r.scopeId=:nid OR r.scopeId=:hid OR r.scopeId=:lid)")
    List<AlertRule> findApplicableRules(@Param("tid") UUID tenantId, @Param("nid") UUID networkId, @Param("hid") UUID hospitalId, @Param("lid") UUID labId);
    Optional<AlertRule> findByTenantIdAndScopeIdAndSensorType(UUID tenantId, UUID scopeId, SensorType sensorType);
}
