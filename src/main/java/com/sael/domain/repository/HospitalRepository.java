package com.sael.domain.repository;
import com.sael.domain.entity.Hospital;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface HospitalRepository extends JpaRepository<Hospital,UUID> {
    Page<Hospital> findAllByNetwork_IdAndDeletedAtIsNull(UUID networkId, Pageable p);
    Optional<Hospital> findByIdAndDeletedAtIsNull(UUID id);
    List<Hospital> findAllByTenant_IdAndDeletedAtIsNull(UUID tenantId);
    long countByNetwork_IdAndDeletedAtIsNull(UUID networkId);
}
