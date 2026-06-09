package com.sael.domain.repository;
import com.sael.domain.entity.KpiSubmission;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface KpiRepository extends JpaRepository<KpiSubmission,UUID> {
    Page<KpiSubmission> findAllByLab_IdOrderByYearDescWeekNumberDesc(UUID labId, Pageable p);
    Optional<KpiSubmission> findByLab_IdAndYearAndWeekNumber(UUID labId, int year, int weekNumber);
    @Query("SELECT k FROM KpiSubmission k WHERE k.lab.hospital.network.id=:nid ORDER BY k.submittedAt DESC")
    List<KpiSubmission> findByNetworkId(@Param("nid") UUID networkId);
    @Query("SELECT k FROM KpiSubmission k WHERE k.lab.id=:lid AND k.submittedAt BETWEEN :from AND :to ORDER BY k.year, k.weekNumber")
    List<KpiSubmission> findByLabAndDateRange(@Param("lid") UUID labId, @Param("from") java.time.OffsetDateTime from, @Param("to") java.time.OffsetDateTime to);

    @Query("SELECT k FROM KpiSubmission k LEFT JOIN FETCH k.lab LEFT JOIN FETCH k.submittedByUser WHERE k.tenantId = :tid ORDER BY k.submittedAt DESC")
    List<KpiSubmission> findByTenantId(@Param("tid") UUID tenantId);

    @Query("SELECT k FROM KpiSubmission k LEFT JOIN FETCH k.lab LEFT JOIN FETCH k.submittedByUser WHERE k.tenantId = :tid AND k.lab.id = :lid ORDER BY k.submittedAt DESC")
    List<KpiSubmission> findByTenantIdAndLabId(@Param("tid") UUID tenantId, @Param("lid") UUID labId);
}
