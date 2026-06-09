package com.sael.domain.repository;
import com.sael.domain.entity.AiInsight;
import com.sael.domain.enums.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AiInsightRepository extends JpaRepository<AiInsight,UUID> {
    @Query("SELECT i FROM AiInsight i WHERE i.tenantId=:tid AND (coalesce(:labId, i.labId) = i.labId) AND (coalesce(:priority, i.priority) = i.priority) AND (coalesce(:type, i.insightType) = i.insightType) ORDER BY i.createdAt DESC")
    Page<AiInsight> findFiltered(@Param("tid") UUID tenantId, @Param("labId") UUID labId, @Param("priority") InsightPriority priority, @Param("type") InsightType type, Pageable p);
}
