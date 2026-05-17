package com.sael.domain.entity;
import com.sael.domain.enums.ScopeEntityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="user_scopes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserScope {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="userId",nullable=false) private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name="entityType",nullable=false)
    private ScopeEntityType entityType;

    @Column(name="entityId",nullable=false) private UUID entityId;
    @Column(name="grantedBy") private UUID grantedBy;
    @Column(name="grantedAt",updatable=false) private OffsetDateTime grantedAt;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();grantedAt=OffsetDateTime.now();}
}
