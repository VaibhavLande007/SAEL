package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="tenantId",nullable=false) private Tenant tenant;
    @Column(nullable=false,unique=true) private String email;
    @Column(name="passwordHash",nullable=false) private String passwordHash;
    @Column(name="fullName",nullable=false) private String fullName;
    private String phone;
    @Column(name="avatarUrl") private String avatarUrl;
    @Column(name="isActive",nullable=false) @Builder.Default private Boolean isActive=true;
    @Column(name="lastLoginAt") private OffsetDateTime lastLoginAt;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="updatedAt") private OffsetDateTime updatedAt;
    @Column(name="deletedAt") private OffsetDateTime deletedAt;
    @OneToMany(mappedBy="user",fetch=FetchType.LAZY,cascade=CascadeType.ALL) @Builder.Default private List<UserRole> userRoles=new ArrayList<>();
    @OneToMany(mappedBy="user",fetch=FetchType.LAZY,cascade=CascadeType.ALL) @Builder.Default private Set<UserScope> userScopes=new HashSet<>();    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
    public boolean isDeleted(){return deletedAt!=null;}
}
