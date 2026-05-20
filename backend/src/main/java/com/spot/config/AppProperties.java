package com.spot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Trading trading = new Trading();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Trading getTrading() {
        return trading;
    }

    public void setTrading(Trading trading) {
        this.trading = trading;
    }

    public static class Jwt {
        private String issuer;
        private String secret;
        private long ttlSeconds;

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Security {
        private boolean allowDevOtpEcho;
        private boolean allowDevAdminKey;
        private String devAdminKey;

        public boolean isAllowDevOtpEcho() {
            return allowDevOtpEcho;
        }

        public void setAllowDevOtpEcho(boolean allowDevOtpEcho) {
            this.allowDevOtpEcho = allowDevOtpEcho;
        }

        public boolean isAllowDevAdminKey() {
            return allowDevAdminKey;
        }

        public void setAllowDevAdminKey(boolean allowDevAdminKey) {
            this.allowDevAdminKey = allowDevAdminKey;
        }

        public String getDevAdminKey() {
            return devAdminKey;
        }

        public void setDevAdminKey(String devAdminKey) {
            this.devAdminKey = devAdminKey;
        }
    }

    public static class Trading {
        private int defaultFeeBps;
        private int klineIntervalSeconds;
        private String engineType = "db";
        private String consensusType = "local";
        private Kafka kafka = new Kafka();
        private Aeron aeron = new Aeron();
        private MarketMaker marketMaker = new MarketMaker();

        public int getDefaultFeeBps() {
            return defaultFeeBps;
        }

        public void setDefaultFeeBps(int defaultFeeBps) {
            this.defaultFeeBps = defaultFeeBps;
        }

        public int getKlineIntervalSeconds() {
            return klineIntervalSeconds;
        }

        public void setKlineIntervalSeconds(int klineIntervalSeconds) {
            this.klineIntervalSeconds = klineIntervalSeconds;
        }

        public String getEngineType() {
            return engineType;
        }

        public void setEngineType(String engineType) {
            this.engineType = engineType;
        }

        public String getConsensusType() {
            return consensusType;
        }

        public void setConsensusType(String consensusType) {
            this.consensusType = consensusType;
        }

        public Kafka getKafka() {
            return kafka;
        }

        public void setKafka(Kafka kafka) {
            this.kafka = kafka;
        }

        public Aeron getAeron() {
            return aeron;
        }

        public void setAeron(Aeron aeron) {
            this.aeron = aeron;
        }

        public MarketMaker getMarketMaker() {
            return marketMaker;
        }

        public void setMarketMaker(MarketMaker marketMaker) {
            this.marketMaker = marketMaker;
        }
    }

    public static class Kafka {
        private boolean enabled;
        private String commandTopic = "trade.command";
        private String resultTopic = "trade.result";
        private String commandGroupId = "spot-trade-command";
        private String replyGroupId = "spot-trade-reply";
        private long replyTimeoutMs = 5000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCommandTopic() {
            return commandTopic;
        }

        public void setCommandTopic(String commandTopic) {
            this.commandTopic = commandTopic;
        }

        public String getResultTopic() {
            return resultTopic;
        }

        public void setResultTopic(String resultTopic) {
            this.resultTopic = resultTopic;
        }

        public String getCommandGroupId() {
            return commandGroupId;
        }

        public void setCommandGroupId(String commandGroupId) {
            this.commandGroupId = commandGroupId;
        }

        public String getReplyGroupId() {
            return replyGroupId;
        }

        public void setReplyGroupId(String replyGroupId) {
            this.replyGroupId = replyGroupId;
        }

        public long getReplyTimeoutMs() {
            return replyTimeoutMs;
        }

        public void setReplyTimeoutMs(long replyTimeoutMs) {
            this.replyTimeoutMs = replyTimeoutMs;
        }
    }

    public static class Aeron {
        private boolean enabled;
        private boolean embeddedDriver = true;
        private String directoryName = "";
        private String commandChannel = "aeron:ipc";
        private String resultChannel = "aeron:ipc";
        private int commandStreamId = 1001;
        private int resultStreamId = 1002;
        private int fragmentLimit = 10;
        private int offerRetryCount = 200;
        private int idleSleepMs = 1;
        private long replyTimeoutMs = 3000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEmbeddedDriver() {
            return embeddedDriver;
        }

        public void setEmbeddedDriver(boolean embeddedDriver) {
            this.embeddedDriver = embeddedDriver;
        }

        public String getDirectoryName() {
            return directoryName;
        }

        public void setDirectoryName(String directoryName) {
            this.directoryName = directoryName;
        }

        public String getCommandChannel() {
            return commandChannel;
        }

        public void setCommandChannel(String commandChannel) {
            this.commandChannel = commandChannel;
        }

        public String getResultChannel() {
            return resultChannel;
        }

        public void setResultChannel(String resultChannel) {
            this.resultChannel = resultChannel;
        }

        public int getCommandStreamId() {
            return commandStreamId;
        }

        public void setCommandStreamId(int commandStreamId) {
            this.commandStreamId = commandStreamId;
        }

        public int getResultStreamId() {
            return resultStreamId;
        }

        public void setResultStreamId(int resultStreamId) {
            this.resultStreamId = resultStreamId;
        }

        public int getFragmentLimit() {
            return fragmentLimit;
        }

        public void setFragmentLimit(int fragmentLimit) {
            this.fragmentLimit = fragmentLimit;
        }

        public int getOfferRetryCount() {
            return offerRetryCount;
        }

        public void setOfferRetryCount(int offerRetryCount) {
            this.offerRetryCount = offerRetryCount;
        }

        public int getIdleSleepMs() {
            return idleSleepMs;
        }

        public void setIdleSleepMs(int idleSleepMs) {
            this.idleSleepMs = idleSleepMs;
        }

        public long getReplyTimeoutMs() {
            return replyTimeoutMs;
        }

        public void setReplyTimeoutMs(long replyTimeoutMs) {
            this.replyTimeoutMs = replyTimeoutMs;
        }
    }

    public static class MarketMaker {
        private boolean enabled = true;
        private boolean useBinancePrice = true;
        private int levels = 10;
        private int spreadBps = 6;
        private int refreshMs = 1200;
        private int tradePulseMs = 1500;
        private int binanceWatchdogMs = 15000;
        private String minQty = "0.001";
        private String maxQty = "0.08";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isUseBinancePrice() {
            return useBinancePrice;
        }

        public void setUseBinancePrice(boolean useBinancePrice) {
            this.useBinancePrice = useBinancePrice;
        }

        public int getLevels() {
            return levels;
        }

        public void setLevels(int levels) {
            this.levels = levels;
        }

        public int getSpreadBps() {
            return spreadBps;
        }

        public void setSpreadBps(int spreadBps) {
            this.spreadBps = spreadBps;
        }

        public int getRefreshMs() {
            return refreshMs;
        }

        public void setRefreshMs(int refreshMs) {
            this.refreshMs = refreshMs;
        }

        public int getTradePulseMs() {
            return tradePulseMs;
        }

        public void setTradePulseMs(int tradePulseMs) {
            this.tradePulseMs = tradePulseMs;
        }

        public int getBinanceWatchdogMs() {
            return binanceWatchdogMs;
        }

        public void setBinanceWatchdogMs(int binanceWatchdogMs) {
            this.binanceWatchdogMs = binanceWatchdogMs;
        }

        public String getMinQty() {
            return minQty;
        }

        public void setMinQty(String minQty) {
            this.minQty = minQty;
        }

        public String getMaxQty() {
            return maxQty;
        }

        public void setMaxQty(String maxQty) {
            this.maxQty = maxQty;
        }
    }
}
