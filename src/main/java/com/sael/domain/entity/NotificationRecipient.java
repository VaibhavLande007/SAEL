package com.sael.domain.entity;
import com.sael.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="notification_recipients")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationRecipient {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(name="networkId") private UUID networkId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default private NotificationChannel channel=NotificationChannel.WHATSAPP;

    @Column(nullable=false) private String address;
    private String label;
    @Column(name="isActive") @Builder.Default private Boolean isActive=true;
    @Column(name="receivesCritical") @Builder.Default private Boolean receivesCritical=true;
    @Column(name="receivesWarning") @Builder.Default private Boolean receivesWarning=true;
    @Column(name="receivesDailySummary") @Builder.Default private Boolean receivesDailySummary=false;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
