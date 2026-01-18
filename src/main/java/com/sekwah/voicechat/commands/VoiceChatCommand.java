package com.sekwah.voicechat.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.sekwah.voicechat.gui.VoiceChatLinkGui;
import com.sekwah.voicechat.server.VoiceChatService;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.CompletableFuture;

public class VoiceChatCommand extends AbstractAsyncCommand {

    private final VoiceChatService service;

    public VoiceChatCommand(VoiceChatService service) {
        super("voice", "Voice chat related commands");
        this.service = service;

        this.addAliases("vc", "voicechat");

        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected @NotNull CompletableFuture<Void> executeAsync(@NotNull CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if(sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                if (ref != null) {
                    PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                    if( playerRef != null) {
                        String link = service.createSessionUrl(playerRef.getUuid(), player.getDisplayName());
                        player.getPageManager().openCustomPage(ref, store, new VoiceChatLinkGui(playerRef, link));
                    }
                }
            }, world);
        }
        return CompletableFuture.completedFuture(null);
    }
}
