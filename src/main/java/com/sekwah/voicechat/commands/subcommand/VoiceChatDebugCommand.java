package com.sekwah.voicechat.commands.subcommand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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

import java.awt.Color;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.server.core.command.commands.player.inventory.InventorySeeCommand.MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD;

public class VoiceChatDebugCommand extends AbstractAsyncCommand {

    private final VoiceChatService service;

    public VoiceChatDebugCommand(VoiceChatService service) {
        super("debug", "Creates a debug voice chat token for testing.");
        this.service = service;
    }

    @Override
    protected @NotNull CompletableFuture<Void> executeAsync(@NotNull CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                commandContext.sendMessage(MESSAGE_COMMANDS_ERRORS_PLAYER_NOT_IN_WORLD);
                return CompletableFuture.completedFuture(null);
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            return CompletableFuture.runAsync(() -> {
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                if (!isOperator(playerRef)) {
                    commandContext.sendMessage(
                        Message.translation("commands.errors.voicechat.debug_requires_op").color(Color.RED)
                    );
                    return;
                }
                String link = service.createDebugSessionUrl();
                player.getPageManager().openCustomPage(ref, store, new VoiceChatLinkGui(playerRef, link));
            }, world);
        }
        return CompletableFuture.completedFuture(null);
    }

    private boolean isOperator(PlayerRef playerRef) {
        PermissionsModule perms = PermissionsModule.get();
        Set<String> groups = perms.getGroupsForUser(playerRef.getUuid());
        return groups.contains("OP");
    }
}
