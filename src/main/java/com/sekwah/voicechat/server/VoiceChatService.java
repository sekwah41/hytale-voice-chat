package com.sekwah.voicechat.server;

import com.hypixel.hytale.server.core.util.Config;
import com.sekwah.voicechat.config.VoiceChatConfig;
import com.sekwah.voicechat.config.VoiceChatSessionsConfig;

import java.time.Duration;
import java.util.UUID;

public class VoiceChatService {

    private final Config<VoiceChatConfig> config;
    private final VoiceChatTokenStore tokens;
    private final VoiceChatRoom room = new VoiceChatRoom();
    private final Config<VoiceChatSessionsConfig> sessionsConfig;

    private VoiceChatServer server;
    private String publicUrl;

    public VoiceChatService(Config<VoiceChatConfig> config, Config<VoiceChatSessionsConfig> sessionsConfig) {
        this.config = config;
        this.sessionsConfig = sessionsConfig;
        this.tokens = new VoiceChatTokenStore(this.sessionsConfig);
    }

    public void start() {
        VoiceChatConfig current = config.get();
        int port = current.getVoiceChatPort();
        publicUrl = resolvePublicUrl(current, port);
        String hostname = resolveHostname(publicUrl);
        server = new VoiceChatServer(port, hostname, tokens, room, current.isVoiceChatDevForwardingEnabled());
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
        tokens.registerUser(userId);
        String token = tokens.createToken(userId, ttl);
        return appendToken(publicUrl, token);
    }

    private String resolvePublicUrl(VoiceChatConfig current, int port) {
        String base = current.getVoiceChatPublicUrl();
        if (base == null || base.isBlank()) {
            if (current.isVoiceChatDevForwardingEnabled()) {
                base = "http://localhost:5173/voice/";
            } else {
                base = "https://localhost";
            }
        }
        return normalizePublicUrl(base, port, current.isVoiceChatDevForwardingEnabled());
    }

    private String normalizePublicUrl(String baseUrl, int port, boolean allowHttp) {
        String normalized = baseUrl;
        if (normalized.startsWith("http://")) {
            if (!allowHttp) {
                normalized = "https://" + normalized.substring("http://".length());
            }
        } else if (!normalized.startsWith("https://")) {
            normalized = (allowHttp ? "http://" : "https://") + normalized;
        }
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null) {
                scheme = allowHttp ? "http" : "https";
            }
            String host = uri.getHost();
            int targetPort = uri.getPort() == -1 ? port : uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                path = "/voice/";
            } else if (!path.startsWith("/voice")) {
                path = path.endsWith("/") ? path + "voice/" : path + "/voice/";
            } else if (!path.endsWith("/")) {
                path = path + "/";
            }
            java.net.URI rebuilt = new java.net.URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    targetPort,
                    path,
                    uri.getQuery(),
                    uri.getFragment()
            );
            return rebuilt.toString();
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private String resolveHostname(String baseUrl) {
        try {
            return java.net.URI.create(baseUrl).getHost();
        } catch (Exception ignored) {
            return "localhost";
        }
    }

    private String appendToken(String baseUrl, String token) {
        if (baseUrl.contains("?")) {
            return baseUrl + "&token=" + token;
        }
        return baseUrl + "?token=" + token;
    }

    public void playerDisconnected(UUID playerUuid) {
        room.disconnectUser(playerUuid);
    }
}
