package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="ai_insights")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AiInsight {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(name="labId") private UUID labId;
    @Column(name="sourceEntityType") private String sourceEntityType;
    @Column(name="sourceEntityId") private UUID sourceEntityId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="insightType",nullable=false)
    private InsightType insightType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false)
    private InsightPriority priority;

    @Column(name="confidenceScore",nullable=false) private Integer confidenceScore;
    @Column(nullable=false) private String title;
    @Column(name="insightText",nullable=false,columnDefinition="TEXT") private String insightText;
    @Column(name="recommendedAction",columnDefinition="TEXT") private String recommendedAction;
    @Column(name="actionTaken",columnDefinition="TEXT") private String actionTaken;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") @Builder.Default private Map<String,Object> metadata=new HashMap<>();
    @Column(name="expiresAt") private OffsetDateTime expiresAt;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="generatedBy")
    @Builder.Default private InsightGenerator generatedBy=InsightGenerator.SIA;

    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    // UI-only field (not in DB — computed on the fly)
    @Transient private Boolean isRead=false;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();}
}
