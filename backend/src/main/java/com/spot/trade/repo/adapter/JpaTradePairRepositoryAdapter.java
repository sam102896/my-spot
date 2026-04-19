package com.spot.trade.repo.adapter;

import com.spot.trade.domain.repository.TradePairRepository;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.repo.TradingPairRepo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaTradePairRepositoryAdapter implements TradePairRepository {
    private final TradingPairRepo pairRepo;

    public JpaTradePairRepositoryAdapter(TradingPairRepo pairRepo) {
        this.pairRepo = pairRepo;
    }

    @Override
    public Optional<TradingPairEntity> findById(UUID pairId) {
        return pairRepo.findById(pairId);
    }

    @Override
    public Optional<TradingPairEntity> findBySymbol(String symbol) {
        return pairRepo.findBySymbol(symbol);
    }
}
