package com.sekwah.voicechat;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.sekwah.voicechat.commands.VoiceChatCommand;
import com.sekwah.voicechat.config.VoiceChatConfig;
import com.sekwah.voicechat.config.VoiceChatSessionsConfig;
import com.sekwah.voicechat.server.VoiceChatService;
import com.sekwah.voicechat.systems.VoicePositionSystem;
import com.sekwah.voicechat.systems.components.VoiceChatComponent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class VoiceChat extends JavaPlugin {

    public static Config<VoiceChatConfig> CONFIG;
    public static Config<VoiceChatSessionsConfig> SESSIONS_CONFIG;

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private VoiceChatService service;
    private ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType;

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
        this.service = new VoiceChatService(CONFIG, SESSIONS_CONFIG);
        this.service.start();
        this.getCommandRegistry().registerCommand(new VoiceChatCommand(this.service));

        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            var playerUuid = event.getPlayerRef().getUuid();
            this.service.playerDisconnected(playerUuid);
        });

        this.voiceChatComponentType = this.getEntityStoreRegistry().registerComponent(VoiceChatComponent.class, VoiceChatComponent::new);
        this.getEntityStoreRegistry().registerSystem(new VoicePositionSystem(this.voiceChatComponentType));
    }

    @Override
    public @Nullable CompletableFuture<Void> preLoad() {
        return super.preLoad();
    }

    public ComponentType<EntityStore, VoiceChatComponent> getPoisonComponentType() {
        return this.voiceChatComponentType;
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        service.stop();
    }
}
