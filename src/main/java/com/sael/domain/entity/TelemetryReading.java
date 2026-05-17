package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity @Table(name="telemetry_readings")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TelemetryReading {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(name="deviceId",nullable=false) private UUID deviceId;
    @Column(name="labId",nullable=false) private UUID labId;
    @Column(name="co2Ppm",precision=10,scale=2) private BigDecimal co2Ppm;
    @Column(name="tvocPpb",precision=10,scale=2) private BigDecimal tvocPpb;
    @Column(name="temperatureC",precision=6,scale=2) private BigDecimal temperatureC;
    @Column(name="humidityPct",precision=5,scale=2) private BigDecimal humidityPct;
    @Column(name="pm25UgM3",precision=8,scale=2) private BigDecimal pm25UgM3;
    @Column(name="pm10UgM3",precision=8,scale=2) private BigDecimal pm10UgM3;
    @Column(name="pm1UgM3",precision=8,scale=2) private BigDecimal pm1UgM3;
    @Column(name="doorIsOpen") private Boolean doorIsOpen;
    @Column(name="doorChanged",nullable=false) @Builder.Default private Boolean doorChanged=false;
    @Column(name="firmwareVersion",length=100) private String firmwareVersion;
    @Column(name="signalStrengthDbm") private Short signalStrengthDbm;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="qualityFlag")
    @Builder.Default private QualityFlag qualityFlag=QualityFlag.GOOD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="ingestionSrc")
    @Builder.Default private IngestionSource ingestionSrc=IngestionSource.MQTT;

    @Column(name="recordedAt",nullable=false) private OffsetDateTime recordedAt;
}