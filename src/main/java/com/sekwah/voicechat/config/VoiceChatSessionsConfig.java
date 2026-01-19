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
                    (config, value) -> config.sessionTokens = new HashMap<>(value),
                    (config) -> config.sessionTokens).add()
            .build();

    private Map<UUID, String> sessionTokens = new HashMap<>();

    public String getSessionToken(UUID uuid) {
        return sessionTokens.get(uuid);
    }
}
