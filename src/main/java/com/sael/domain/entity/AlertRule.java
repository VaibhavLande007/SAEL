package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="alert_rules")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertRule {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(name="sensorId") private UUID sensorId;
    @Enumerated(EnumType.STRING) @Column(name="scopeType",nullable=false) private AlertScopeType scopeType;
    @Column(name="scopeId") private UUID scopeId;
    @Enumerated(EnumType.STRING) @Column(name="sensorType",nullable=false) private SensorType sensorType;
    @Column(name="ruleName",nullable=false) private String ruleName;
    @Column(name="warnMin",precision=10,scale=4) private BigDecimal warnMin;
    @Column(name="warnMax",precision=10,scale=4) private BigDecimal warnMax;
    @Column(name="critMin",precision=10,scale=4) private BigDecimal critMin;
    @Column(name="critMax",precision=10,scale=4) private BigDecimal critMax;
    @Column(name="cooldownMin") @Builder.Default private Integer cooldownMin=5;
    @Column(name="isActive") @Builder.Default private Boolean isActive=true;
    @Column(name="createdBy") private UUID createdBy;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
