package com.spot.seed;

import com.spot.account.entity.AssetEntity;
import com.spot.account.entity.UserEntity;
import com.spot.account.model.KycStatus;
import com.spot.account.model.LedgerType;
import com.spot.account.repo.AssetRepo;
import com.spot.account.repo.LedgerRepo;
import com.spot.account.repo.UserRepo;
import com.spot.account.service.WalletService;
import com.spot.common.money.Atomic;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.repo.TradingPairRepo;
import java.util.Optional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {
    private final AssetRepo assetRepo;
    private final TradingPairRepo pairRepo;
    private final UserRepo userRepo;
    private final LedgerRepo ledgerRepo;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AssetRepo assetRepo, TradingPairRepo pairRepo, UserRepo userRepo, LedgerRepo ledgerRepo,
            WalletService walletService, PasswordEncoder passwordEncoder) {
        this.assetRepo = assetRepo;
        this.pairRepo = pairRepo;
        this.userRepo = userRepo;
        this.ledgerRepo = ledgerRepo;
        this.walletService = walletService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        init();
    }

    @Transactional
    public void init() {
        AssetEntity btc = ensureAsset("BTC", "Bitcoin", 8);
        AssetEntity eth = ensureAsset("ETH", "Ethereum", 8);
        AssetEntity usdt = ensureAsset("USDT", "Tether", 8);

        ensurePair("BTCUSDT", btc, usdt, Atomic.parse("0.0001", 8), Atomic.parse("5", 8), 10, 2, 6);
        ensurePair("ETHUSDT", eth, usdt, Atomic.parse("0.001", 8), Atomic.parse("5", 8), 10, 2, 6);

        UserEntity alice = ensureUser("alice@example.com", null, "Passw0rd!", "123456", "Alice");
        UserEntity bob = ensureUser("bob@example.com", null, "Passw0rd!", "123456", "Bob");

        if (!ledgerRepo.existsByRefTypeAndRefId("SEED", "ALICE")) {
            walletService.addAvailable(alice.getId(), usdt.getId(), Atomic.parse("10000", 8), LedgerType.DEPOSIT,
                    "SEED", "ALICE");
            walletService.addAvailable(alice.getId(), btc.getId(), Atomic.parse("1", 8), LedgerType.DEPOSIT, "SEED",
                    "ALICE");
        }
        if (!ledgerRepo.existsByRefTypeAndRefId("SEED", "BOB")) {
            walletService.addAvailable(bob.getId(), usdt.getId(), Atomic.parse("10000", 8), LedgerType.DEPOSIT, "SEED",
                    "BOB");
            walletService.addAvailable(bob.getId(), eth.getId(), Atomic.parse("20", 8), LedgerType.DEPOSIT, "SEED",
                    "BOB");
        }
    }

    private AssetEntity ensureAsset(String symbol, String name, int decimals) {
        Optional<AssetEntity> existing = assetRepo.findBySymbol(symbol);
        if (existing.isPresent()) {
            return existing.get();
        }
        AssetEntity a = new AssetEntity();
        a.setSymbol(symbol);
        a.setName(name);
        a.setDecimals(decimals);
        return assetRepo.save(a);
    }

    private TradingPairEntity ensurePair(String symbol, AssetEntity base, AssetEntity quote, long minQty,
            long minNotional, int feeBps, int priceDecimals, int qtyDecimals) {
        Optional<TradingPairEntity> existing = pairRepo.findBySymbol(symbol);
        if (existing.isPresent()) {
            return existing.get();
        }
        TradingPairEntity p = new TradingPairEntity();
        p.setSymbol(symbol);
        p.setBaseAssetId(base.getId());
        p.setQuoteAssetId(quote.getId());
        p.setMinQty(minQty);
        p.setMinNotional(minNotional);
        p.setFeeBps(feeBps);
        p.setPriceDecimals(priceDecimals);
        p.setQtyDecimals(qtyDecimals);
        return pairRepo.save(p);
    }

    private UserEntity ensureUser(String email, String phone, String password, String fundPassword, String name) {
        Optional<UserEntity> existing = userRepo.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        UserEntity u = new UserEntity();
        u.setEmail(email);
        u.setPhone(phone);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setFundPasswordHash(passwordEncoder.encode(fundPassword));
        u.setName(name);
        u.setKycStatus(KycStatus.VERIFIED);
        return userRepo.save(u);
    }
}
