package com.sekwah.voicechat.systems;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.sekwah.voicechat.server.VoiceChatRoom;
import com.sekwah.voicechat.systems.components.VoiceChatComponent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

public class VoiceDataBroadcastSystem extends TickingSystem<EntityStore> {
    private static final Gson GSON = new Gson();

    private final ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType;
    private final VoiceChatRoom room;

    public VoiceDataBroadcastSystem(ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType, VoiceChatRoom room) {
        this.voiceChatComponentType = voiceChatComponentType;
        this.room = room;
    }

    @Override
    public void tick(float v, int i, @NotNull Store<EntityStore> store) {
        Universe universe = Universe.get();
        for (PlayerRef player : universe.getPlayers()) {
            Ref<EntityStore> ref = player.getReference();
            if(ref == null) {
                continue;
            }
            VoiceChatComponent voiceChatComponent = store.getComponent(ref, this.voiceChatComponentType);
            if (voiceChatComponent == null) {
                continue;
            }
            UUID userId = player.getUuid();
            if (!room.isUserConnected(userId)) {
                voiceChatComponent.markPositionDirty = false;
                voiceChatComponent.markRotationDirty = false;
                store.putComponent(ref, this.voiceChatComponentType, voiceChatComponent);
                continue;
            }
            String clientId = room.getClientId(userId);
            if (clientId == null) {
                continue;
            }

            boolean shouldSendPosition = voiceChatComponent.markPositionDirty
                    && voiceChatComponent.currentPosition != null;
            if (shouldSendPosition) {
                JsonObject message = new JsonObject();
                message.addProperty("type", "position");
                message.addProperty("id", clientId);
                message.add("position", GSON.toJsonTree(voiceChatComponent.currentPosition));
                room.broadcast(message, null);
                voiceChatComponent.markPositionDirty = false;
            }

            boolean shouldSendRotation = voiceChatComponent.markRotationDirty
                    && voiceChatComponent.currentRotation != null;
            if (shouldSendRotation) {
                JsonObject message = new JsonObject();
                message.addProperty("type", "rotation");
                message.addProperty("id", clientId);
                message.add("rotation", GSON.toJsonTree(voiceChatComponent.currentRotation));
                room.broadcast(message, null);
                voiceChatComponent.markRotationDirty = false;
            }

            store.putComponent(ref, this.voiceChatComponentType, voiceChatComponent);
        }
    }

    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.AFTER, VoicePositionSystem.class)
        );
    }
}
