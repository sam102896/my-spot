package com.spot.trade.repo;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepo extends JpaRepository<OrderEntity, UUID> {
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<OrderEntity> findByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId, List<OrderStatus> statuses,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderEntity> findWithLockById(UUID id);

    @Query("""
            select o from OrderEntity o
            where o.pairId = :pairId
              and o.side = 'SELL'
              and o.status in ('NEW','PARTIALLY_FILLED')
              and (:limitPrice is null or (o.price is not null and o.price <= :limitPrice))
            order by o.price asc, o.createdAt asc
            """)
    List<OrderEntity> findAsksForBuy(@Param("pairId") UUID pairId, @Param("limitPrice") Long limitPrice,
            Pageable pageable);

    @Query("""
            select o from OrderEntity o
            where o.pairId = :pairId
              and o.side = 'BUY'
              and o.status in ('NEW','PARTIALLY_FILLED')
              and (:limitPrice is null or (o.price is not null and o.price >= :limitPrice))
            order by o.price desc, o.createdAt asc
            """)
    List<OrderEntity> findBidsForSell(@Param("pairId") UUID pairId, @Param("limitPrice") Long limitPrice,
            Pageable pageable);

    @Query("""
            select o from OrderEntity o
            where o.pairId = :pairId
              and o.side = 'BUY'
              and o.status in ('NEW','PARTIALLY_FILLED')
              and o.price is not null
            order by o.price desc, o.createdAt asc
            """)
    List<OrderEntity> findBidsBook(@Param("pairId") UUID pairId, Pageable pageable);

    @Query("""
            select o from OrderEntity o
            where o.pairId = :pairId
              and o.side = 'SELL'
              and o.status in ('NEW','PARTIALLY_FILLED')
              and o.price is not null
            order by o.price asc, o.createdAt asc
            """)
    List<OrderEntity> findAsksBook(@Param("pairId") UUID pairId, Pageable pageable);
}
