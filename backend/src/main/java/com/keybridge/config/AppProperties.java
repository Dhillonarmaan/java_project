package com.keybridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "keybridge")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Keystore keystore = new Keystore();
    private Expiry expiry = new Expiry();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Keystore getKeystore() {
        return keystore;
    }

    public void setKeystore(Keystore keystore) {
        this.keystore = keystore;
    }

    public Expiry getExpiry() {
        return expiry;
    }

    public void setExpiry(Expiry expiry) {
        this.expiry = expiry;
    }

    public static class Jwt {
        private String secret;
        private long expiryMinutes = 120;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(long expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }
    }

    public static class Keystore {
        private String dir = "./keystore-data";
        private String password;
        private String rootAlias = "keybridge-root";

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRootAlias() {
            return rootAlias;
        }

        public void setRootAlias(String rootAlias) {
            this.rootAlias = rootAlias;
        }
    }

    public static class Expiry {
        private int warnDays = 30;
        private String cron = "0 0 1 * * *";

        public int getWarnDays() {
            return warnDays;
        }

        public void setWarnDays(int warnDays) {
            this.warnDays = warnDays;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
