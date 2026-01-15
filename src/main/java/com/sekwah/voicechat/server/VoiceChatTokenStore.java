package com.sekwah.voicechat.server;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceChatTokenStore {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String createToken(Duration ttl) {
        purgeExpired();
        String token = new UUID(random.nextLong(), random.nextLong()).toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        tokens.put(token, expiresAt);
        return token;
    }

    public boolean consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expiresAt = tokens.remove(token);
        if (expiresAt == null) {
            return false;
        }
        return expiresAt >= System.currentTimeMillis();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
