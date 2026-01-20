package com.sekwah.voicechat.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.sekwah.voicechat.VoiceChat;
import com.sekwah.voicechat.systems.components.VoiceChatComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VoicePositionSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType;

    public VoicePositionSystem(ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType) {
        this.voiceChatComponentType = voiceChatComponentType;
    }

    @Override
    public void tick(float dt, int index, @NotNull ArchetypeChunk<EntityStore> archetypeChunk, @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        TransformComponent transformComp = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        VoiceChatComponent voiceChatComponent = commandBuffer.getComponent(ref, this.voiceChatComponentType);
        if(voiceChatComponent == null) {
            voiceChatComponent = new VoiceChatComponent();
        }

        voiceChatComponent.timeSinceLastUpdate += dt;

        if(voiceChatComponent.timeSinceLastUpdate > 0.1) {
            voiceChatComponent.timeSinceLastUpdate = 0;
            VoiceChat.LOGGER.atInfo().log("Updating voice chat position for player %s to %s", ref.getStore().getComponent(ref, Player.getComponentType()).getDisplayName(), transformComp.getPosition());
        }

        Vector3d position = transformComp.getPosition();


        commandBuffer.putComponent(ref, this.voiceChatComponentType, voiceChatComponent);
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        var playerType = Player.getComponentType();
        var transformType = TransformComponent.getComponentType();
        if (playerType == null || transformType == null) {
            VoiceChat.LOGGER.atWarning().log(
                "VoicePositionSystem disabled: component types missing (player=%s, transform=%s)",
                playerType,
                transformType
            );
            return Query.not(Query.any());
        }
        return Query.and(playerType, transformType);
    }
}
