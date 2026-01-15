package com.sekwah.voicechat.server;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.BindException;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoiceChatServer {

    private final int port;
    private final VoiceChatTokenStore tokens;
    private final VoiceChatRoom room;
    private final HytaleLogger logger;
    private final boolean devForwardingEnabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Gson gson = new Gson();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public VoiceChatServer(int port, VoiceChatTokenStore tokens, VoiceChatRoom room, HytaleLogger logger, boolean devForwardingEnabled) {
        this.port = port;
        this.tokens = tokens;
        this.room = room;
        this.logger = logger;
        this.devForwardingEnabled = devForwardingEnabled;
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
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
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
                    logger.atInfo().log("Voice chat server listening on port %s", port);
                    channel.closeFuture().syncUninterruptibly();
                    break;
                } catch (Exception e) {
                    if (isBindException(e)) {
                        logger.atWarning().withCause(e).log("Voice chat bind failed on port %s (attempt %s/5), retrying in 1s.", port, attempts);
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
            logger.atSevere().withCause(e).log("Voice chat server failed to start.");
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
}
