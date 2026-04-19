package com.spot.trade.repo.adapter;

import com.spot.trade.domain.repository.TradeOrderRepository;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderStatus;
import com.spot.trade.repo.OrderRepo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class JpaTradeOrderRepositoryAdapter implements TradeOrderRepository {
    private final OrderRepo orderRepo;

    public JpaTradeOrderRepositoryAdapter(OrderRepo orderRepo) {
        this.orderRepo = orderRepo;
    }

    @Override
    public Optional<OrderEntity> findById(UUID orderId) {
        return orderRepo.findById(orderId);
    }

    @Override
    public Optional<OrderEntity> findWithLockById(UUID orderId) {
        return orderRepo.findWithLockById(orderId);
    }

    @Override
    public OrderEntity save(OrderEntity order) {
        return orderRepo.save(order);
    }

    @Override
    public List<OrderEntity> findOpenOrders(UUID userId, List<OrderStatus> statuses, Pageable pageable) {
        return orderRepo.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, statuses, pageable);
    }

    @Override
    public List<OrderEntity> findOrderHistory(UUID userId, Pageable pageable) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public List<OrderEntity> findAsksBook(UUID pairId, Pageable pageable) {
        return orderRepo.findAsksBook(pairId, pageable);
    }
}
