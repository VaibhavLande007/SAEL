package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="devices")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Device {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(name="incubatorId") private UUID incubatorId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="labId") private Lab lab;
    @Column(name="deviceUid",nullable=false,unique=true) private String deviceUid;
    @Column(name="mqttClientId",unique=true) private String mqttClientId;
    @Column(name="deviceKey") private String deviceKey;
    @Column(name="firmwareVersion") private String firmwareVersion;
    @Column(name="onlineStatus") @Builder.Default private Boolean onlineStatus=false;
    @Column(name="lastSeenAt") private OffsetDateTime lastSeenAt;
    @Column(name="batteryLevel") private Integer batteryLevel;
    @Column(name="signalStrength") private Integer signalStrength;
    @Column(name="registeredAt") private OffsetDateTime registeredAt;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @OneToMany(mappedBy="device",fetch=FetchType.LAZY) @Builder.Default private List<Sensor> sensors=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();if(registeredAt==null)registeredAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
