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

        public MarketMaker getMarketMaker() {
            return marketMaker;
        }

        public void setMarketMaker(MarketMaker marketMaker) {
            this.marketMaker = marketMaker;
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
