package com.sekwah.voicechat;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.sekwah.voicechat.commands.VoiceChatCommand;
import com.sekwah.voicechat.config.VoiceChatConfig;
import com.sekwah.voicechat.config.VoiceChatSessionsConfig;
import com.sekwah.voicechat.server.VoiceChatService;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class VoiceChat extends JavaPlugin {

    public static Config<VoiceChatConfig> CONFIG;
    public static Config<VoiceChatSessionsConfig> SESSIONS_CONFIG;

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private VoiceChatService service;

    public VoiceChat(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
        CONFIG = this.withConfig("VoiceChat", VoiceChatConfig.CODEC);
        SESSIONS_CONFIG = this.withConfig("VoiceChatSessions", VoiceChatSessionsConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();
        CONFIG.save();
        SESSIONS_CONFIG.save();
        this.service = new VoiceChatService(CONFIG);
        this.service.start();
        this.getCommandRegistry().registerCommand(new VoiceChatCommand(this.service));
    }

    @Override
    public @Nullable CompletableFuture<Void> preLoad() {
        return super.preLoad();
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        service.stop();
    }
}
