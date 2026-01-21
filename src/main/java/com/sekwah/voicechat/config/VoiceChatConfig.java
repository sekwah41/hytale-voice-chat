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
            .append(new KeyedCodec<Integer>("PeerUpdatesPerSecond", Codec.INTEGER),
                    (config, value) -> config.PeerUpdatesPerSecond = value,
                    (config) -> config.PeerUpdatesPerSecond).add()
            .append(new KeyedCodec<Integer>("AudioEffectUpdatesPerSecond", Codec.INTEGER),
                    (config, value) -> config.AudioEffectUpdatesPerSecond = value,
                    (config) -> config.AudioEffectUpdatesPerSecond).add()
            .append(new KeyedCodec<Integer>("VoiceChatTokenTtlSeconds", Codec.INTEGER),
                    (config, value) -> config.VoiceChatTokenTtlSeconds = value,
                    (config) -> config.VoiceChatTokenTtlSeconds).add()
            .append(new KeyedCodec<Boolean>("VoiceChatDevForwardingEnabled", Codec.BOOLEAN),
                    (config, value) -> config.VoiceChatDevForwardingEnabled = value,
                    (config) -> config.VoiceChatDevForwardingEnabled).add()
            .append(new KeyedCodec<Double>("FullVolumeRange", Codec.DOUBLE),
                    (config, value) -> config.FullVolumeRange = value,
                    (config) -> config.FullVolumeRange).add()
            .append(new KeyedCodec<Double>("FallOffRange", Codec.DOUBLE),
                    (config, value) -> config.FallOffRange = value,
                    (config) -> config.FallOffRange).add()
            .append(new KeyedCodec<Double>("AdditionalPeerConnectionRange", Codec.DOUBLE),
                    (config, value) -> config.AdditionalPeerConnectionRange = value,
                    (config) -> config.AdditionalPeerConnectionRange).add()
            .build();

    private int VoiceChatPort = 24454;
    private String VoiceChatPublicUrl = "";
    private int VoiceChatTokenTtlSeconds = 300;
    private int PeerUpdatesPerSecond = 10;
    private int AudioEffectUpdatesPerSecond = 4;
    private boolean VoiceChatDevForwardingEnabled = false;
    private double FullVolumeRange = 12.0;
    private double FallOffRange = 16.0;
    private double AdditionalPeerConnectionRange = 8.0;

    public int getVoiceChatPort() {
        return VoiceChatPort;
    }

    public String getVoiceChatPublicUrl() {
        return VoiceChatPublicUrl;
    }

    public int getVoiceChatTokenTtlSeconds() {
        return VoiceChatTokenTtlSeconds;
    }

    public int getPeerUpdatesPerSecond() {
        return PeerUpdatesPerSecond;
    }

    public int getAudioEffectUpdatesPerSecond() {
        return AudioEffectUpdatesPerSecond;
    }

    public boolean isVoiceChatDevForwardingEnabled() {
        return VoiceChatDevForwardingEnabled;
    }

    public double getFullVolumeRange() {
        return FullVolumeRange;
    }

    public double getFallOffRange() {
        return FallOffRange;
    }

    public double getAdditionalPeerConnectionRange() {
        return AdditionalPeerConnectionRange;
    }
}
