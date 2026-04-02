# 性能压测（JMH）

本模块用于对 my-spot 的撮合/下单路径做基准测试，输出吞吐（TPS）与延迟（SampleTime）。

## 构建

在仓库根目录执行：

```bash
mvn -q -pl perf -am package
```

生成可执行 JMH 包：
- `perf/target/my-spot-perf.jar`

## 运行

查看可用 benchmark：

```bash
java -jar perf/target/my-spot-perf.jar -l
```

运行全部 benchmark（示例：4 线程、热身/测量时间较短）：

```bash
java -Djmh.ignoreLock=true -jar perf/target/my-spot-perf.jar -t 4 -wi 2 -i 3 -f 1
```

仅运行某个类：

```bash
java -Djmh.ignoreLock=true -jar perf/target/my-spot-perf.jar ".*SpringTradingBenchmark.*" -t 4 -wi 2 -i 3 -f 1
```

输出结果（JSON）：

```bash
java -Djmh.ignoreLock=true -jar perf/target/my-spot-perf.jar -rf json -rff perf-result.json -t 4 -wi 2 -i 3 -f 1
```

## Benchmark 说明

- `OrderBookMatchBenchmark`
  - 纯内存撮合扫描（用于观察订单簿规模对延迟/吞吐的影响）
  - 参数：`bookSize = 10 / 100 / 1000 / 10000`

- `SpringTradingBenchmark`
  - 启动 Spring 容器（无 WebServer）、使用 H2 in-memory、调用 `TradingService.placeOrder` 走真实下单+撮合路径
  - 参数：`bookSize = 10 / 100 / 1000`（预先挂卖单深度）
