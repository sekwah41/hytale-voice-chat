package com.sekwah.voicechat.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class VoiceChatHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final boolean devForwardingEnabled;

    public VoiceChatHttpHandler(boolean devForwardingEnabled) {
        this.devForwardingEnabled = devForwardingEnabled;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = new QueryStringDecoder(request.uri()).path();
        if (devForwardingEnabled && path.startsWith("/voice-dev")) {
            redirectToDevServer(ctx, request);
            return;
        }
        if (path.startsWith("/voice/ws")) {
            ctx.fireChannelRead(request.retain());
            return;
        }

        if (!"GET".equals(request.method().name())) {
            sendResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "Method not allowed.");
            return;
        }

        if ("/".equals(path)) {
            redirect(ctx, request, "/voice/");
            return;
        }

        switch (path) {
            case "/voice", "/voice/":
                sendResource(ctx, request, "web/index.html", "text/html; charset=UTF-8");
                break;
            case "/voice/app.js":
                sendResource(ctx, request, "web/app.js", "application/javascript");
                break;
            case "/voice/style.css":
                sendResource(ctx, request, "web/style.css", "text/css");
                break;
            default:
                sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND, "Not found.");
        }
    }

    private void sendResource(ChannelHandlerContext ctx, FullHttpRequest request, String resourcePath, String contentType) {
        byte[] bytes = readResource(resourcePath);
        if (bytes == null) {
            sendResponse(ctx, request, HttpResponseStatus.NOT_FOUND, "Not found.");
            return;
        }
        ByteBuf content = Unpooled.wrappedBuffer(bytes);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        writeResponse(ctx, request, response);
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String message) {
        ByteBuf content = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        writeResponse(ctx, request, response);
    }

    private void redirect(ChannelHandlerContext ctx, FullHttpRequest request, String location) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, location);
        writeResponse(ctx, request, response);
    }

    private void redirectToDevServer(ChannelHandlerContext ctx, FullHttpRequest request) {
        String hostHeader = request.headers().get(HttpHeaderNames.HOST);
        String host = (hostHeader == null || hostHeader.isBlank()) ? "localhost" : hostHeader;
        String hostOnly = extractHost(host);
        String target = "http://" + hostOnly + ":5173" + mapDevPath(request.uri());
        redirect(ctx, request, target);
    }

    private String extractHost(String hostHeader) {
        if (hostHeader.startsWith("[")) {
            int end = hostHeader.indexOf(']');
            return end > 0 ? hostHeader.substring(0, end + 1) : hostHeader;
        }
        int colonIndex = hostHeader.lastIndexOf(':');
        if (colonIndex > 0) {
            return hostHeader.substring(0, colonIndex);
        }
        return hostHeader;
    }

    private String mapDevPath(String uri) {
        if (uri.equals("/voice-dev")) {
            return "/";
        }
        if (uri.startsWith("/voice-dev?")) {
            return "/?" + uri.substring("/voice-dev?".length());
        }
        if (uri.startsWith("/voice-dev/")) {
            return uri.substring("/voice-dev".length());
        }
        return "/";
    }

    private void writeResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private byte[] readResource(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
