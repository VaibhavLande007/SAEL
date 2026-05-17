package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="system_configs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SystemConfig {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @Column(nullable=false) private String key;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private Map<String,Object> value;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
