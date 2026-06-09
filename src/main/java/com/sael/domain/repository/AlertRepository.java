package com.sael.domain.repository;
import com.sael.domain.entity.Alert;
import com.sael.domain.enums.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;
public interface AlertRepository extends JpaRepository<Alert,UUID> {
    @Query("SELECT a FROM Alert a WHERE a.tenantId=:tid AND (coalesce(:labId, a.lab.id) = a.lab.id) AND (coalesce(:status, a.status) = a.status) AND (coalesce(:severity, a.severity) = a.severity) ORDER BY a.triggeredAt DESC")
    Page<Alert> findFiltered(@Param("tid") UUID tenantId, @Param("labId") UUID labId, @Param("status") AlertStatus status, @Param("severity") AlertSeverity severity, Pageable p);
    long countByLab_IdAndStatus(UUID labId, AlertStatus status);
    long countByTenantIdAndStatusAndTriggeredAtAfter(UUID tenantId, AlertStatus status, OffsetDateTime after);
    long countByTenantIdAndTriggeredAtAfter(UUID tenantId, OffsetDateTime after);
    long countByTenantIdAndSeverityAndTriggeredAtAfter(UUID tenantId, AlertSeverity severity, OffsetDateTime after);
    long countByStatus(AlertStatus status);
    @Query("SELECT a FROM Alert a WHERE (:status IS NULL OR a.status=:status) AND (:severity IS NULL OR a.severity=:severity) ORDER BY a.triggeredAt DESC")
    Page<Alert> findGlobalFiltered(@Param("status") AlertStatus status, @Param("severity") AlertSeverity severity, Pageable p);
    @Query("SELECT a FROM Alert a WHERE a.lab.id=:labId AND (:status IS NULL OR a.status=:status) ORDER BY a.triggeredAt DESC")
    Page<Alert> findByLabAndStatus(@Param("labId") UUID labId, @Param("status") AlertStatus status, Pageable p);
}
