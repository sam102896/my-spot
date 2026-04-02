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
    }
}
