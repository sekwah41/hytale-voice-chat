package com.sekwah.voicechat.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class VoiceChatConfig {

    public static final BuilderCodec<VoiceChatConfig> CODEC = BuilderCodec.builder(VoiceChatConfig.class, VoiceChatConfig::new)
            .append(new KeyedCodec<Integer>("VoiceChatPort", Codec.INTEGER),
                    (config, value) -> config.VoiceChatPort = value,
                    (config) -> config.VoiceChatPort).add()
            .append(new KeyedCodec<String>("VoiceChatPublicUrl", Codec.STRING),
                    (config, value) -> config.VoiceChatPublicUrl = value,
                    (config) -> config.VoiceChatPublicUrl).add()
            .append(new KeyedCodec<Integer>("VoiceChatTokenTtlSeconds", Codec.INTEGER),
                    (config, value) -> config.VoiceChatTokenTtlSeconds = value,
                    (config) -> config.VoiceChatTokenTtlSeconds).add()
            .append(new KeyedCodec<Boolean>("VoiceChatDevForwardingEnabled", Codec.BOOLEAN),
                    (config, value) -> config.VoiceChatDevForwardingEnabled = value,
                    (config) -> config.VoiceChatDevForwardingEnabled).add()
            .build();

    private int VoiceChatPort = 24454;
    private String VoiceChatPublicUrl = "";
    private int VoiceChatTokenTtlSeconds = 300;
    private boolean VoiceChatDevForwardingEnabled = false;

    public int getVoiceChatPort() {
        return VoiceChatPort;
    }

    public String getVoiceChatPublicUrl() {
        return VoiceChatPublicUrl;
    }

    public int getVoiceChatTokenTtlSeconds() {
        return VoiceChatTokenTtlSeconds;
    }

    public boolean isVoiceChatDevForwardingEnabled() {
        return VoiceChatDevForwardingEnabled;
    }
}
