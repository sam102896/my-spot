package com.spot.perf.jmh;

import java.util.Arrays;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Threads(4)
public class OrderBookMatchBenchmark {
    @State(Scope.Benchmark)
    public static class BookState {
        @Param({"10", "100", "1000", "10000"})
        public int bookSize;

        @Param({"2000"})
        public long midPrice;

        @Param({"100000", "1000000"})
        public long qtyBase;

        public long[] askPrices;
        public long[] askRemainingQty;

        @Setup(Level.Trial)
        public void setup() {
            askPrices = new long[bookSize];
            askRemainingQty = new long[bookSize];
            long baseQty = 100_000_000_000L;
            for (int i = 0; i < bookSize; i++) {
                askPrices[i] = midPrice + i;
                askRemainingQty[i] = baseQty;
            }
        }
    }

    @Benchmark
    public void matchMarketBuy(BookState st, Blackhole bh) {
        long qty = st.qtyBase + ThreadLocalRandom.current().nextLong(st.qtyBase);
        long filled = 0L;
        long quote = 0L;
        long[] rem = st.askRemainingQty;
        long[] px = st.askPrices;
        for (int i = 0; i < rem.length && qty > 0; i++) {
            long r = rem[i];
            if (r <= 0) {
                continue;
            }
            long take = Math.min(qty, r);
            rem[i] = r - take;
            qty -= take;
            filled += take;
            quote += take * px[i];
        }
        if (qty > 0) {
            Arrays.fill(rem, 100_000_000_000L);
        }
        bh.consume(filled);
        bh.consume(quote);
    }
}
