package com.sael.domain.repository;
import com.sael.domain.entity.UserScope;
import com.sael.domain.enums.ScopeEntityType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface UserScopeRepository extends JpaRepository<UserScope,UUID> {
    List<UserScope> findAllByUser_Id(UUID userId);
    boolean existsByUser_IdAndEntityTypeAndEntityId(UUID userId, ScopeEntityType type, UUID entityId);
    @Modifying @Query("DELETE FROM UserScope us WHERE us.user.id=:uid AND us.entityType=:t AND us.entityId=:eid")
    void deleteScope(@Param("uid") UUID userId, @Param("t") ScopeEntityType type, @Param("eid") UUID entityId);
}
