package com.sekwah.voicechat.server;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;
import com.sekwah.voicechat.config.VoiceChatConfig;

import java.time.Duration;
import java.util.UUID;

public class VoiceChatService {

    private final Config<VoiceChatConfig> config;
    private final HytaleLogger logger;
    private final VoiceChatTokenStore tokens = new VoiceChatTokenStore();
    private final VoiceChatRoom room = new VoiceChatRoom();
    private VoiceChatServer server;
    private String publicUrl;

    public VoiceChatService(Config<VoiceChatConfig> config, HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    public void start() {
        VoiceChatConfig current = config.get();
        int port = current.getVoiceChatPort();
        publicUrl = resolvePublicUrl(current, port);
        server = new VoiceChatServer(port, tokens, room, logger, current.isVoiceChatDevForwardingEnabled());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "voicechat-shutdown"));
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    public String createSessionUrl(UUID userId) {
        VoiceChatConfig current = config.get();
        Duration ttl = Duration.ofSeconds(Math.max(30, current.getVoiceChatTokenTtlSeconds()));
        String token = tokens.createToken(userId, ttl);
        return appendToken(publicUrl, token);
    }

    private String resolvePublicUrl(VoiceChatConfig current, int port) {
        String base = current.getVoiceChatPublicUrl();
        if (base == null || base.isBlank()) {
            if (current.isVoiceChatDevForwardingEnabled()) {
                base = "http://localhost:5173/";
            } else {
                base = "http://localhost:" + port + "/voice/";
            }
        }
        return base;
    }

    private String appendToken(String baseUrl, String token) {
        if (baseUrl.contains("?")) {
            return baseUrl + "&token=" + token;
        }
        return baseUrl + "?token=" + token;
    }
}
