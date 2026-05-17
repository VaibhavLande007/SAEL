package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="roles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId") private UUID tenantId;
    @Column(nullable=false) private String name;
    private String description;
    @Column(name="isSystem",nullable=false) @Builder.Default private Boolean isSystem=false;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @Column(name="createdBy") private UUID createdBy;
    @OneToMany(mappedBy="role",fetch=FetchType.LAZY) @Builder.Default private List<UserRole> userRoles=new ArrayList<>();
    @OneToMany(mappedBy="role",fetch=FetchType.LAZY,cascade=CascadeType.ALL) @Builder.Default private List<RolePermission> rolePermissions=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();}
}
