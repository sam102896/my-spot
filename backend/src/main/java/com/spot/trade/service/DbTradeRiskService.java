package com.spot.trade.service;

import com.spot.account.entity.UserEntity;
import com.spot.account.model.KycStatus;
import com.spot.common.api.ApiException;
import com.spot.common.crypto.Hashing;
import com.spot.common.money.Atomic;
import com.spot.trade.entity.OrderIntentEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderType;
import com.spot.trade.repo.OrderIntentRepo;
import com.spot.trade.repo.OrderRepo;
import com.spot.trade.repo.TradingPairRepo;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DbTradeRiskService implements TradeRiskService, TradeEngineAware {
    private final com.spot.account.repo.UserRepo userRepo;
    private final TradingPairRepo pairRepo;
    private final OrderIntentRepo intentRepo;
    private final OrderRepo orderRepo;

    public DbTradeRiskService(com.spot.account.repo.UserRepo userRepo, TradingPairRepo pairRepo,
            OrderIntentRepo intentRepo, OrderRepo orderRepo) {
        this.userRepo = userRepo;
        this.pairRepo = pairRepo;
        this.intentRepo = intentRepo;
        this.orderRepo = orderRepo;
    }

    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.DB;
    }

    @Override
    public ValidatedPlaceOrder validatePlaceOrder(TradeOrderService.PlaceOrderCommand command) {
        UserEntity user = userRepo.findById(command.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        if (user.getKycStatus() != KycStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KYC_REQUIRED", "完成基础KYC后才能交易");
        }

        TradingPairEntity pair = pairRepo.findBySymbol(command.pairSymbol().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIR_NOT_FOUND", "交易对不存在"));
        if (command.idemKey() == null || command.idemKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_REQUIRED", "缺少X-Idempotency-Key");
        }

        Long priceAtomic = null;
        if (command.type() == OrderType.LIMIT) {
            if (command.price() == null || command.price().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_REQUIRED", "限价单必须提供价格");
            }
            priceAtomic = Atomic.parse(command.price(), Atomic.DEFAULT_DECIMALS);
            if (priceAtomic <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "价格必须大于0");
            }
        }

        long qtyAtomic = Atomic.parse(command.qty(), Atomic.DEFAULT_DECIMALS);
        if (qtyAtomic < pair.getMinQty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_QTY", "小于最小下单数量");
        }
        if (command.type() == OrderType.LIMIT) {
            long notional = Atomic.quoteQtyFromPriceQty(priceAtomic, qtyAtomic);
            if (notional < pair.getMinNotional()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_NOTIONAL", "小于最小交易金额");
            }
        }

        String requestHash = Hashing.sha256Hex(pair.getSymbol() + "|" + command.side() + "|" + command.type() + "|"
                + (priceAtomic == null ? "" : priceAtomic) + "|" + qtyAtomic + "|"
                + (command.clientOrderId() == null ? "" : command.clientOrderId()));
        OrderIntentEntity intent = intentRepo.findByUserIdAndIdemKey(command.userId(), command.idemKey()).orElse(null);
        if (intent != null) {
            if (!intent.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "幂等Key已被用于不同请求");
            }
            if (intent.getOrderId() != null) {
                orderRepo.findById(intent.getOrderId())
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_FOUND", "幂等记录异常"));
            }
        } else {
            intent = new OrderIntentEntity();
            intent.setUserId(command.userId());
            intent.setIdemKey(command.idemKey());
            intent.setRequestHash(requestHash);
            intent.setCreatedAt(Instant.now());
            intent = intentRepo.save(intent);
        }
        return new ValidatedPlaceOrder(user, pair, priceAtomic, qtyAtomic, intent);
    }
}
