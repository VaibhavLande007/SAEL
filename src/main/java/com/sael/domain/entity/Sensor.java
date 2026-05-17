package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="sensors")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Sensor {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="deviceId",nullable=false) private Device device;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="sensorType",nullable=false)
    private SensorType sensorType;

    @Column(name="sensorCode") private String sensorCode;
    private String unit;
    @Column(name="calibrationOffset",precision=8,scale=4) @Builder.Default private BigDecimal calibrationOffset=BigDecimal.ZERO;
    @Column(name="warnMin",precision=10,scale=4) private BigDecimal warnMin;
    @Column(name="warnMax",precision=10,scale=4) private BigDecimal warnMax;
    @Column(name="critMin",precision=10,scale=4) private BigDecimal critMin;
    @Column(name="critMax",precision=10,scale=4) private BigDecimal critMax;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default private SensorStatus status=SensorStatus.ACTIVE;

    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
