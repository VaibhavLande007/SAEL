package com.sael.domain.repository;
import com.sael.domain.entity.AiInsight;
import com.sael.domain.enums.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AiInsightRepository extends JpaRepository<AiInsight,UUID> {
    @Query("SELECT i FROM AiInsight i WHERE i.tenantId=:tid AND (:labId IS NULL OR i.labId=:labId) AND (:priority IS NULL OR i.priority=:priority) AND (:type IS NULL OR i.insightType=:type) ORDER BY i.createdAt DESC")
    Page<AiInsight> findFiltered(@Param("tid") UUID tenantId, @Param("labId") UUID labId, @Param("priority") InsightPriority priority, @Param("type") InsightType type, Pageable p);
}
