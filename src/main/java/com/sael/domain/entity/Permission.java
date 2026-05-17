package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name="permissions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Permission {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(nullable=false,unique=true) private String name;
    private String description;
    @Column(name="createdAt",updatable=false) private OffsetDateTime createdAt;
    @OneToMany(mappedBy="permission",fetch=FetchType.LAZY) @Builder.Default private List<RolePermission> rolePermissions=new ArrayList<>();
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();createdAt=OffsetDateTime.now();}
}
