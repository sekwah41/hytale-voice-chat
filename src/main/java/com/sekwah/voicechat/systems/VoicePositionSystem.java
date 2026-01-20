package com.sekwah.voicechat.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.sekwah.voicechat.VoiceChat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VoicePositionSystem extends EntityTickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int index, @NotNull ArchetypeChunk<EntityStore> archetypeChunk, @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        TransformComponent transformComp = ref.getStore().getComponent(ref, TransformComponent.getComponentType());

        Vector3d position = transformComp.getPosition();

        VoiceChat.LOGGER.atInfo().log("Player %s is at position %s", ref.getStore().getComponent(ref, Player.getComponentType()).getDisplayName(), position);
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
