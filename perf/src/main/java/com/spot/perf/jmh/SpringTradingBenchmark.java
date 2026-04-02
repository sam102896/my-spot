package com.spot.perf.jmh;

import com.spot.SpotApplication;
import com.spot.account.entity.AssetEntity;
import com.spot.account.entity.UserEntity;
import com.spot.account.model.LedgerType;
import com.spot.account.repo.AssetRepo;
import com.spot.account.repo.UserRepo;
import com.spot.account.service.WalletService;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderType;
import com.spot.trade.repo.TradingPairRepo;
import com.spot.trade.service.TradingService;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Threads(4)
public class SpringTradingBenchmark {
    @State(Scope.Benchmark)
    public static class CtxState {
        @Param({"10", "100", "1000"})
        public int bookSize;

        @Param({"0.001", "0.01"})
        public String orderQty;

        public ConfigurableApplicationContext ctx;
        public TradingService tradingService;
        public TradingPairEntity pair;
        public UUID aliceId;
        public UUID bobId;
        public UUID quoteAssetId;
        public UUID baseAssetId;

        @Setup(Level.Trial)
        public void setup() {
            ctx = new SpringApplicationBuilder(SpotApplication.class).run(
                    "--server.port=0",
                    "--spring.main.web-application-type=servlet",
                    "--spring.datasource.url=jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                    "--spring.jpa.hibernate.ddl-auto=create-drop",
                    "--spring.task.scheduling.enabled=false",
                    "--logging.level.root=WARN"
            );

            tradingService = ctx.getBean(TradingService.class);
            WalletService walletService = ctx.getBean(WalletService.class);
            TradingPairRepo pairRepo = ctx.getBean(TradingPairRepo.class);
            AssetRepo assetRepo = ctx.getBean(AssetRepo.class);
            UserRepo userRepo = ctx.getBean(UserRepo.class);

            pair = pairRepo.findBySymbol("ETHUSDT").orElseThrow();
            baseAssetId = pair.getBaseAssetId();
            quoteAssetId = pair.getQuoteAssetId();

            aliceId = userRepo.findByEmail("alice@example.com").map(UserEntity::getId).orElseThrow();
            bobId = userRepo.findByEmail("bob@example.com").map(UserEntity::getId).orElseThrow();

            AssetEntity quote = assetRepo.findById(quoteAssetId).orElseThrow();
            AssetEntity base = assetRepo.findById(baseAssetId).orElseThrow();

            long topUpQuote = 10_000_000L * pow10(quote.getDecimals());
            long topUpBase = 200_000L * pow10(base.getDecimals());

            walletService.addAvailable(aliceId, quoteAssetId, topUpQuote, LedgerType.DEPOSIT, "BENCH", "ALICE_TOPUP");
            walletService.addAvailable(bobId, baseAssetId, topUpBase, LedgerType.DEPOSIT, "BENCH", "BOB_TOPUP");

            seedAsks(tradingService, bobId, bookSize);
        }

        private void seedAsks(TradingService svc, UUID sellerId, int n) {
            for (int i = 0; i < n; i++) {
                long px = 2000L + i;
                svc.placeOrder(
                        sellerId,
                        "ETHUSDT",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        Long.toString(px),
                        "100",
                        null,
                        UUID.randomUUID().toString(),
                        "127.0.0.1",
                        "bench"
                );
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (ctx != null) {
                ctx.close();
            }
        }

        private long pow10(int decimals) {
            long v = 1L;
            for (int i = 0; i < decimals; i++) {
                v *= 10L;
            }
            return v;
        }
    }

    @Benchmark
    public void placeLimitMatch(CtxState st, Blackhole bh) {
        String idem1 = UUID.randomUUID().toString();
        String idem2 = UUID.randomUUID().toString();

        long px = 2000L + ThreadLocalRandom.current().nextInt(5);

        var sell = st.tradingService.placeOrder(
                st.bobId,
                "ETHUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                Long.toString(px),
                st.orderQty,
                null,
                idem1,
                "127.0.0.1",
                "bench-bob"
        );
        var buy = st.tradingService.placeOrder(
                st.aliceId,
                "ETHUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                Long.toString(px),
                st.orderQty,
                null,
                idem2,
                "127.0.0.1",
                "bench-alice"
        );

        bh.consume(sell.getId());
        bh.consume(buy.getId());
        bh.consume(buy.getStatus());
    }
}
