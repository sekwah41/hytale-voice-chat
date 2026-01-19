package com.sekwah.voicechat.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.RawJsonCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VoiceChatSessionsConfig {

    private static MapCodec SESSION_TOKENS_CODEC = new MapCodec(Codec.UUID_STRING, HashMap::new);

    public static final BuilderCodec CODEC = BuilderCodec.builder(VoiceChatSessionsConfig.class, VoiceChatSessionsConfig::new)
            .append(new KeyedCodec<>("SessionTokens", SESSION_TOKENS_CODEC),
                    (config, value) -> {
                        config.sessionTokens = new HashMap<>(value);
                        config.sessionTokenToUserUUID = new HashMap<>();
                        for (Map.Entry<UUID, String> entry : value.entrySet()) {
                            config.sessionTokenToUserUUID.put(entry.getValue(), entry.getKey());
                        }
                    },
                    (config) -> config.sessionTokens).add()
            .build();

    private Map<UUID, String> sessionTokens = new HashMap<>();
    private Map<String, UUID> sessionTokenToUserUUID = new HashMap<>();

    public String getSessionToken(UUID uuid) {
        return sessionTokens.get(uuid);
    }

    public UUID getUserUUIDFromToken(String token) {
        return this.sessionTokenToUserUUID.get(token);
    }

    public void setSessionToken(UUID uuid, String token) {
        String existingToken = sessionTokens.put(uuid, token);
        if (existingToken != null) {
            sessionTokenToUserUUID.remove(existingToken);
        }
        sessionTokenToUserUUID.put(token, uuid);
    }
}
