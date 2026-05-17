package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="labs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Lab {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="tenantId",nullable=false) private Tenant tenant;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="hospitalId",nullable=false) private Hospital hospital;
    @Column(nullable=false) private String name;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "labType")
    private LabType labType;
    private String timezone;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "operationalStatus")
    @Builder.Default
    private OperationalStatus operationalStatus = OperationalStatus.ACTIVE;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @Column(name="deletedAt") private OffsetDateTime deletedAt;
    @OneToMany(mappedBy="lab",fetch=FetchType.LAZY) @Builder.Default private List<Alert> alerts=new ArrayList<>();
    @OneToMany(mappedBy="lab",fetch=FetchType.LAZY) @Builder.Default private List<KpiSubmission> kpiSubmissions=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
