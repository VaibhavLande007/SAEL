package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="alerts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Alert {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="labId",nullable=false) private Lab lab;
    @Column(name="sensorId") private UUID sensorId;
    @Column(name="metricColumn",length=50) private String metricColumn;
    @Column(name="deviceId",nullable=false) private UUID deviceId;
    @Column(name="alertRuleId") private UUID alertRuleId;
    @Column(nullable=false) private String parameter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false)
    private AlertSeverity severity;

    @Column(nullable=false,columnDefinition="TEXT") private String message;
    @Column(name="triggerValue",nullable=false,precision=10,scale=4) private BigDecimal triggerValue;
    @Column(name="thresholdValue",nullable=false,precision=10,scale=4) private BigDecimal thresholdValue;
    @Column(name="isPredicted") @Builder.Default private Boolean isPredicted=false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default private AlertStatus status=AlertStatus.ACTIVE;

    @Column(name="acknowledgedBy") private UUID acknowledgedBy;
    @Column(name="acknowledgedAt") private OffsetDateTime acknowledgedAt;
    @Column(name="resolvedAt") private OffsetDateTime resolvedAt;
    @Column(name="triggeredAt") private OffsetDateTime triggeredAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="acknowledgedBy",insertable=false,updatable=false) private User acknowledgedByUser;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();if(triggeredAt==null)triggeredAt=OffsetDateTime.now();}
}
