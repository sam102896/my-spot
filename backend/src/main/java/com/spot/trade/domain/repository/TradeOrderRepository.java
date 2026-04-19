package com.spot.trade.domain.repository;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface TradeOrderRepository {
    Optional<OrderEntity> findById(UUID orderId);

    Optional<OrderEntity> findWithLockById(UUID orderId);

    OrderEntity save(OrderEntity order);

    List<OrderEntity> findOpenOrders(UUID userId, List<OrderStatus> statuses, Pageable pageable);

    List<OrderEntity> findOrderHistory(UUID userId, Pageable pageable);

    List<OrderEntity> findAsksBook(UUID pairId, Pageable pageable);
}
