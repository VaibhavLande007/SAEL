package com.sael.domain.repository;
import com.sael.domain.entity.Network;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NetworkRepository extends JpaRepository<Network,UUID> {
    Page<Network> findAllByTenant_IdAndDeletedAtIsNull(UUID tenantId, Pageable p);
    Optional<Network> findByIdAndTenant_IdAndDeletedAtIsNull(UUID id, UUID tenantId);
    List<Network> findAllByTenant_IdAndDeletedAtIsNull(UUID tenantId);
}
