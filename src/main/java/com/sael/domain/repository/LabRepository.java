package com.sael.domain.repository;
import com.sael.domain.entity.Lab;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface LabRepository extends JpaRepository<Lab,UUID> {
    Page<Lab> findAllByHospital_IdAndDeletedAtIsNull(UUID hospitalId, Pageable p);
    Optional<Lab> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Lab> findByIdAndTenant_IdAndDeletedAtIsNull(UUID id, UUID tenantId);
    List<Lab> findAllByTenant_IdAndDeletedAtIsNull(UUID tenantId);
    @Query("SELECT l FROM Lab l WHERE l.tenant.id=:tid AND l.deletedAt IS NULL AND (:nid IS NULL OR l.hospital.network.id=:nid)")
    Page<Lab> findAllScoped(@Param("tid") UUID tenantId, @Param("nid") UUID networkId, Pageable p);
    long countByHospital_IdAndDeletedAtIsNull(UUID hospitalId);
    long countByHospital_Network_IdAndDeletedAtIsNull(UUID networkId);
}
