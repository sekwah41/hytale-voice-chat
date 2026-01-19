package com.sekwah.voicechat.server;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceChatTokenStore {

    private final Map<String, TokenEntry> tokensByValue = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> tokensByUser = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public void registerUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must be provided");
        }
    }

    public String createToken(UUID userId, Duration ttl) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must be provided");
        }
        purgeExpired();
        String token = new UUID(random.nextLong(), random.nextLong()).toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        tokensByValue.put(token, new TokenEntry(userId, expiresAt));
        tokensByUser
            .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
            .put(token, expiresAt);
        return token;
    }

    public boolean consumeToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        TokenEntry entry = tokensByValue.remove(token);
        if (entry == null) {
            return false;
        }
        removeTokenForUser(entry.userId, token);
        return entry.expiresAt >= System.currentTimeMillis();
    }

    public UUID consumeTokenForUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenEntry entry = tokensByValue.remove(token);
        if (entry == null) {
            return null;
        }
        removeTokenForUser(entry.userId, token);
        if (entry.expiresAt < System.currentTimeMillis()) {
            return null;
        }
        return entry.userId;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        tokensByValue.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt < now) {
                removeTokenForUser(entry.getValue().userId, entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void removeTokenForUser(UUID userId, String token) {
        Map<String, Long> userTokens = tokensByUser.get(userId);
        if (userTokens == null) {
            return;
        }
        userTokens.remove(token);
        if (userTokens.isEmpty()) {
            tokensByUser.remove(userId, userTokens);
        }
    }

    private static final class TokenEntry {
        private final UUID userId;
        private final long expiresAt;

        private TokenEntry(UUID userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}
