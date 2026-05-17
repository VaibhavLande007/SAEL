package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="networks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Network {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="tenantId",nullable=false) private Tenant tenant;
    @Column(nullable=false) private String name;
    @Builder.Default private String timezone="Asia/Kolkata";
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @Column(name="deletedAt") private OffsetDateTime deletedAt;
    @OneToMany(mappedBy="network",fetch=FetchType.LAZY) @Builder.Default private List<Hospital> hospitals=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
