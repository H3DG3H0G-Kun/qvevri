package com.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Auth auth = new Auth();
    private Jwt jwt = new Jwt();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public static class Auth {
        /** When true, unknown usernames are auto-registered on login (dev convenience). */
        private boolean autoRegister = true;

        public boolean isAutoRegister() {
            return autoRegister;
        }

        public void setAutoRegister(boolean autoRegister) {
            this.autoRegister = autoRegister;
        }
    }

    public static class Jwt {
        /**
         * HMAC-SHA256 signing secret.  Must be at least 32 characters.
         * In production supply via the {@code app.jwt.secret} property or env-var.
         */
        private String secret = "cXZldnJpLWRldi1zZWNyZXQta2V5LW11c3QtYmUtYXQtbGVhc3QtMzItY2hhcnM=";

        /** Token lifetime in seconds.  Default 24 h. */
        private long expirySeconds = 86400L;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirySeconds() {
            return expirySeconds;
        }

        public void setExpirySeconds(long expirySeconds) {
            this.expirySeconds = expirySeconds;
        }
    }
}
