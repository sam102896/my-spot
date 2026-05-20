package com.spot.trade.repo.adapter;

import com.spot.trade.domain.repository.TradeOrderIntentRepository;
import com.spot.trade.entity.OrderIntentEntity;
import com.spot.trade.repo.OrderIntentRepo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaTradeOrderIntentRepositoryAdapter implements TradeOrderIntentRepository {
    private final OrderIntentRepo intentRepo;

    public JpaTradeOrderIntentRepositoryAdapter(OrderIntentRepo intentRepo) {
        this.intentRepo = intentRepo;
    }

    @Override
    public Optional<OrderIntentEntity> findByUserIdAndIdemKey(UUID userId, String idemKey) {
        return intentRepo.findByUserIdAndIdemKey(userId, idemKey);
    }

    @Override
    public OrderIntentEntity save(OrderIntentEntity intent) {
        return intentRepo.save(intent);
    }
}
