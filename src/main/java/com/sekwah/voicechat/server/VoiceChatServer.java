package com.sekwah.voicechat.server;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import com.sekwah.voicechat.VoiceChat;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.nio.NioIoHandle;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.BindException;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceChatServer {

    private final int port;
    private final VoiceChatTokenStore tokens;
    private final VoiceChatRoom room;
    private final boolean devForwardingEnabled;
    private final SslContext sslContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public VoiceChatServer(int port, String hostname, VoiceChatTokenStore tokens, VoiceChatRoom room, boolean devForwardingEnabled) {
        this.port = port;
        this.tokens = tokens;
        this.room = room;
        this.devForwardingEnabled = devForwardingEnabled;
        this.sslContext = buildSslContext(hostname);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread serverThread = new Thread(this::startServer, "voicechat-netty");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void stop() {
        running.set(false);
        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    private void startServer() {
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            if (sslContext != null) {
                                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                            }
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new VoiceChatHttpHandler(devForwardingEnabled));
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/voice/ws", null, true));
                            ch.pipeline().addLast(new VoiceChatWebSocketHandler(room, tokens, gson));
                        }
                    })
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            int attempts = 0;
            while (running.get() && attempts < 5) {
                attempts++;
                try {
                    channel = bootstrap.bind(port).syncUninterruptibly().channel();
                    VoiceChat.LOGGER.atInfo().log("Server listening on port %s", port);
                    channel.closeFuture().syncUninterruptibly();
                    break;
                } catch (Exception e) {
                    if (isBindException(e)) {
                        VoiceChat.LOGGER.atWarning().withCause(e).log("Voice chat bind failed on port %s (attempt %s/5), retrying in 1s.", port, attempts);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    throw e;
                }
            }
        } catch (Exception e) {
            VoiceChat.LOGGER.atSevere().withCause(e).log("Voice chat server failed to start.");
        } finally {
            stop();
        }
    }

    private boolean isBindException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof BindException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private SslContext buildSslContext(String hostname) {
        try {
            SelfSignedCertificate certificate = new SelfSignedCertificate(hostname);
            return SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
        } catch (Exception e) {
            VoiceChat.LOGGER.atSevere().withCause(e).log("Failed to generate TLS certificate for %s.", hostname);
            return null;
        }
    }
}
