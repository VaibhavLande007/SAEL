package com.sael.domain.repository;
import com.sael.domain.entity.NotificationRecipient;
import com.sael.domain.enums.NotificationChannel;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient,UUID> {
    Page<NotificationRecipient> findAllByNetworkId(UUID networkId, Pageable p);
    List<NotificationRecipient> findAllByNetworkIdAndChannelAndIsActiveTrue(UUID networkId, NotificationChannel channel);
    boolean existsByNetworkIdAndAddress(UUID networkId, String address);
    List<NotificationRecipient> findAllByTenantIdAndChannelOrderByCreatedAtAsc(UUID tenantId, NotificationChannel channel);
    Optional<NotificationRecipient> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<NotificationRecipient> findByTenantIdAndChannelAndAddress(UUID tenantId, NotificationChannel channel, String address);
}
