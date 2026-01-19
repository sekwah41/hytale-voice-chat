package com.sekwah.voicechat.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.logger.HytaleLogger;
import com.sekwah.voicechat.VoiceChat;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;

import java.util.UUID;

public class VoiceChatWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final AttributeKey<String> CLIENT_ID = AttributeKey.valueOf("voicechat_client_id");
    private static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("voicechat_authenticated");
    private static final AttributeKey<UUID> CLIENT_USER_ID = AttributeKey.valueOf("voicechat_user_id");

    private final VoiceChatRoom room;
    private final VoiceChatTokenStore tokens;
    private final Gson gson;

    public VoiceChatWebSocketHandler(VoiceChatRoom room, VoiceChatTokenStore tokens, Gson gson) {
        this.room = room;
        this.tokens = tokens;
        this.gson = gson;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        JsonObject payload;
        try {
            payload = JsonParser.parseString(msg.text()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            sendError(ctx, "Invalid message format.");
            return;
        }

        String type = getString(payload, "type");
        if (type == null) {
            sendError(ctx, "Missing message type.");
            return;
        }

        Boolean authed = ctx.channel().attr(AUTHENTICATED).get();
        if (!Boolean.TRUE.equals(authed)) {
            if (!"hello".equals(type)) {
                sendError(ctx, "Authentication required.");
                return;
            }
            handleHello(ctx, payload);
            return;
        }

        switch (type) {
            case "offer", "answer", "ice":
                forwardSignal(ctx, payload, type);
                break;
            case "mute":
                broadcastState(ctx, payload, "mute", "muted");
                break;
            case "ptt":
                broadcastState(ctx, payload, "ptt", "active");
                break;
            default:
                sendError(ctx, "Unknown message type: " + type);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String id = ctx.channel().attr(CLIENT_ID).get();
        if (id != null) {
            room.remove(id);
            JsonObject leave = new JsonObject();
            leave.addProperty("type", "peer-leave");
            leave.addProperty("id", id);
            room.broadcast(leave, id);
        }
        VoiceChat.LOGGER.atInfo().log("Voice chat client disconnected.");
    }

    private void handleHello(ChannelHandlerContext ctx, JsonObject payload) {
        String token = getString(payload, "token");
        UUID userId = tokens.consumeTokenForUser(token);
        if (userId == null) {
            sendError(ctx, "Invalid or expired token. Please re-run /voice chat command.");
            return;
        }
        if (room.isUserConnected(userId)) {
            sendError(ctx, "Voice chat already connected for this user.");
            return;
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        String userName = tokens.getUserName(userId);
        String nameLabel = (userName == null || userName.isBlank()) ? "Unknown" : userName;
        String clientName = (userName == null || userName.isBlank()) ? "" : userName;
        VoiceChat.LOGGER.atInfo().log("Voice chat client connected: userName=" + nameLabel + ", userId=" + userId + ", clientId=" + id);
        ctx.channel().attr(CLIENT_ID).set(id);
        ctx.channel().attr(AUTHENTICATED).set(true);
        ctx.channel().attr(CLIENT_USER_ID).set(userId);
        JsonElement existingPeers = gson.toJsonTree(room.peerIdsSnapshot());
        room.register(userId, id, ctx.channel());

        JsonObject welcome = new JsonObject();
        welcome.addProperty("type", "welcome");
        welcome.addProperty("id", id);
        welcome.addProperty("userName", clientName);
        welcome.add("peers", existingPeers);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(welcome.toString()));

        JsonObject join = new JsonObject();
        join.addProperty("type", "peer-join");
        join.addProperty("id", id);
        room.broadcast(join, id);
    }

    private void forwardSignal(ChannelHandlerContext ctx, JsonObject payload, String type) {
        String to = getString(payload, "to");
        String from = ctx.channel().attr(CLIENT_ID).get();
        if (to == null || from == null) {
            sendError(ctx, "Missing target.");
            return;
        }
        JsonObject forward = new JsonObject();
        forward.addProperty("type", type);
        forward.addProperty("from", from);
        if (payload.has("sdp")) {
            forward.add("sdp", payload.get("sdp"));
        }
        if (payload.has("candidate")) {
            forward.add("candidate", payload.get("candidate"));
        }
        if (!room.sendTo(to, forward)) {
            sendError(ctx, "Target not available.");
        }
    }

    private void broadcastState(ChannelHandlerContext ctx, JsonObject payload, String type, String field) {
        JsonElement value = payload.get(field);
        if (value == null) {
            sendError(ctx, "Missing state: " + field);
            return;
        }
        String id = ctx.channel().attr(CLIENT_ID).get();
        if (id == null) {
            sendError(ctx, "Missing client id.");
            return;
        }
        room.broadcastState(type, id, field, value);
    }

    private void sendError(ChannelHandlerContext ctx, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", message);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(error.toString()));
    }

    private String getString(JsonObject payload, String key) {
        JsonElement element = payload.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }
}
