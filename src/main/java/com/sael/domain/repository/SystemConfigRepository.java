package com.sael.domain.repository;
import com.sael.domain.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SystemConfigRepository extends JpaRepository<SystemConfig,UUID> {
    Optional<SystemConfig> findByTenantIdAndKey(UUID tenantId, String key);
    List<SystemConfig> findAllByTenantId(UUID tenantId);
}
