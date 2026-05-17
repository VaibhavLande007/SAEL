package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="notification_logs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationLog {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(name="recipientId",nullable=false) private UUID recipientId;
    @Column(name="alertId") private UUID alertId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private NotificationChannel channel;
    @Column(name="messagePreview",columnDefinition="TEXT") private String messagePreview;
    @Enumerated(EnumType.STRING) @Builder.Default private NotificationStatus status=NotificationStatus.PENDING;
    @Builder.Default private Integer attempts=0;
    @Column(name="sentAt") private OffsetDateTime sentAt;
    @Column(name="deliveredAt") private OffsetDateTime deliveredAt;
    @Column(name="errorMessage",columnDefinition="TEXT") private String errorMessage;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="recipientId",insertable=false,updatable=false) private NotificationRecipient recipient;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();}
}
