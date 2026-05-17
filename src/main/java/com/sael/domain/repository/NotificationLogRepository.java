package com.sael.domain.repository;
import com.sael.domain.entity.NotificationLog;
import com.sael.domain.enums.NotificationStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;
public interface NotificationLogRepository extends JpaRepository<NotificationLog,UUID> {
    @Query("SELECT l FROM NotificationLog l WHERE l.recipient.networkId=:nid AND (:status IS NULL OR l.status=:status) AND (:from IS NULL OR l.createdAt>=:from) AND (:to IS NULL OR l.createdAt<=:to) ORDER BY l.createdAt DESC")
    Page<NotificationLog> findByNetwork(@Param("nid") UUID networkId, @Param("status") NotificationStatus status, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable p);
}
