package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="user_roles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(UserRole.UserRoleId.class)
public class UserRole {
    @Id @Column(name="userId") private UUID userId;
    @Id @Column(name="roleId") private UUID roleId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="userId",insertable=false,updatable=false) private User user;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="roleId",insertable=false,updatable=false) private Role role;
    @Column(name="grantedBy") private UUID grantedBy;
    @Column(name="grantedAt",updatable=false) private OffsetDateTime grantedAt;
    @PrePersist void prePersist(){grantedAt=OffsetDateTime.now();}
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UserRoleId implements Serializable { private UUID userId; private UUID roleId; }
}
