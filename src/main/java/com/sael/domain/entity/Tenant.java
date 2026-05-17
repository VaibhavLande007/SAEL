package com.sael.domain.entity;
import com.sael.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="tenants")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Tenant {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(nullable=false) private String name;
    @Column(nullable=false,unique=true) private String slug;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false)
    @Builder.Default private TenantStatus status=TenantStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable=false)
    @Builder.Default private TenantPlan plan=TenantPlan.STARTER;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") @Builder.Default private Map<String,Object> config=new HashMap<>();
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @Column(name="deletedAt") private OffsetDateTime deletedAt;
    @OneToMany(mappedBy="tenant",fetch=FetchType.LAZY) @Builder.Default private List<User> users=new ArrayList<>();
    @OneToMany(mappedBy="tenant",fetch=FetchType.LAZY) @Builder.Default private List<Network> networks=new ArrayList<>();
    @OneToMany(mappedBy="tenant",fetch=FetchType.LAZY) @Builder.Default private List<Hospital> hospitals=new ArrayList<>();
    @OneToMany(mappedBy="tenant",fetch=FetchType.LAZY) @Builder.Default private List<Lab> labs=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
