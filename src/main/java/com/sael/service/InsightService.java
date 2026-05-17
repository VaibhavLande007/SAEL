package com.sael.service;
import com.sael.domain.entity.AiInsight;
import com.sael.domain.enums.*;
import com.sael.domain.repository.AiInsightRepository;
import com.sael.exception.ResourceNotFoundException;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class InsightService {
    private final AiInsightRepository insightRepo;

    public Page<Map<String,Object>> listInsights(UUID labId,String category,String priority,Pageable p){
        UUID tid=TenantContext.requireTenantId();
        InsightPriority pri=priority!=null?InsightPriority.valueOf(priority.toUpperCase()):null;
        InsightType type=category!=null?InsightType.valueOf(category.toUpperCase()):null;
        return insightRepo.findFiltered(tid,labId,pri,type,p).map(this::toMap);
    }

    @Transactional
    public void markRead(UUID insightId){
        UUID tid=TenantContext.requireTenantId();
        insightRepo.findById(insightId)
            .filter(i->i.getTenantId().equals(tid))
            .orElseThrow(()->new ResourceNotFoundException("Insight not found"));
        // is_read is a UI concept — in a full impl store in a separate read_receipts table
        // For now this is a no-op that returns 204
    }

    public Map<String,Object> triggerGeneration(String scope,UUID entityId){
        // In production this queues a job to the AI pipeline
        // For now return an accepted response immediately
        return Map.of(
            "jobId",UUID.randomUUID(),
            "status","queued",
            "message","Insight generation queued. Results will be available shortly."
        );
    }

    private Map<String,Object> toMap(AiInsight i){
        List<String> actions=i.getRecommendedAction()!=null?
            Arrays.asList(i.getRecommendedAction().split("\n")):List.of();
        var m=new LinkedHashMap<String,Object>();
        m.put("id",i.getId());m.put("labId",i.getLabId());m.put("labName","");
        m.put("hospitalName","");
        m.put("category",i.getInsightType().name().toLowerCase());
        m.put("priority",i.getPriority().name().toLowerCase());
        m.put("title",i.getTitle());m.put("body",i.getInsightText());
        m.put("confidence",i.getConfidenceScore()/100.0);
        m.put("recommendedActions",actions);
        m.put("relatedParameter",i.getSourceEntityType());
        m.put("generatedAt",i.getCreatedAt());
        m.put("isRead",false);
        return m;
    }
}
