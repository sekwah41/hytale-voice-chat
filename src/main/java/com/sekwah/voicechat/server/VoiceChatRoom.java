package com.sekwah.voicechat.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// For now there is one giant global room for testing purposes.
// May make it so there's a room per world in the future.
public class VoiceChatRoom {

    private final Map<String, Channel> clients = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientIdsByUser = new ConcurrentHashMap<>();
    private final Map<String, UUID> userIdsByClient = new ConcurrentHashMap<>();

    public void register(UUID userId, String id, Channel channel) {
        clients.put(id, channel);
        clientIdsByUser.put(userId, id);
        userIdsByClient.put(id, userId);
    }

    public void remove(String id) {
        clients.remove(id);
        UUID userId = userIdsByClient.remove(id);
        if (userId != null) {
            clientIdsByUser.remove(userId, id);
        }
    }

    public boolean isUserConnected(UUID userId) {
        if (userId == null) {
            return false;
        }
        return clientIdsByUser.containsKey(userId);
    }

    public boolean disconnectUser(UUID userId) {
        if (userId == null) {
            return false;
        }
        String clientId = clientIdsByUser.get(userId);
        if (clientId == null) {
            return false;
        }
        Channel channel = clients.get(clientId);
        if (channel != null) {
            channel.close();
        } else {
            remove(clientId);
        }
        return true;
    }

    public Collection<String> peerIdsSnapshot() {
        return new ArrayList<>(clients.keySet());
    }

    public boolean sendTo(String id, JsonObject message) {
        Channel channel = clients.get(id);
        if (channel == null || !channel.isActive()) {
            return false;
        }
        channel.writeAndFlush(new TextWebSocketFrame(message.toString()));
        return true;
    }

    public void broadcast(JsonObject message, String excludeId) {
        String payload = message.toString();
        for (Map.Entry<String, Channel> entry : clients.entrySet()) {
            if (excludeId != null && excludeId.equals(entry.getKey())) {
                continue;
            }
            Channel channel = entry.getValue();
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    public void broadcastState(String type, String id, String field, JsonElement value) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        message.addProperty("id", id);
        message.add(field, value);
        broadcast(message, id);
    }
}
