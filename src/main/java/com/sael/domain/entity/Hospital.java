package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="hospitals")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Hospital {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="tenantId",nullable=false) private Tenant tenant;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="networkId",nullable=false) private Network network;
    @Column(nullable=false) private String name;
    private String address;
    private String city;
    private String state;
    @Builder.Default private String country="IN";
    @Column(precision=9,scale=6) private BigDecimal latitude;
    @Column(precision=9,scale=6) private BigDecimal longitude;
    private String phone;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @Column(name="deletedAt") private OffsetDateTime deletedAt;
    @OneToMany(mappedBy="hospital",fetch=FetchType.LAZY) @Builder.Default private List<Lab> labs=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
}
