package com.sekwah.voicechat.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.sekwah.voicechat.VoiceChat;
import com.sekwah.voicechat.server.VoiceChatRoom;
import com.sekwah.voicechat.systems.components.VoiceChatComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VoicePositionSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType;
    private final VoiceChatRoom room;

    public VoicePositionSystem(ComponentType<EntityStore, VoiceChatComponent> voiceChatComponentType, VoiceChatRoom room) {
        this.voiceChatComponentType = voiceChatComponentType;
        this.room = room;
    }

    @Override
    public void tick(float dt, int index, @NotNull ArchetypeChunk<EntityStore> archetypeChunk, @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        UUID userId = playerRef != null ? playerRef.getUuid() : null;
        boolean connected = userId != null && room.isUserConnected(userId);
        VoiceChatComponent voiceChatComponent = commandBuffer.getComponent(ref, this.voiceChatComponentType);

        if (!connected) {
            if (voiceChatComponent != null) {
                commandBuffer.removeComponent(ref, this.voiceChatComponentType);
            }
            return;
        }

        if (voiceChatComponent == null) {
            voiceChatComponent = new VoiceChatComponent();
        }

        TransformComponent transformComp = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        voiceChatComponent.timeSinceLastUpdate += dt;

        if (voiceChatComponent.timeSinceLastUpdate > 0.1) {
            voiceChatComponent.timeSinceLastUpdate = 0;
        }

        ModelTransform rotation = transformComp.getSentTransform();
        Vector3d position = new Vector3d(rotation.position.x, rotation.position.y, rotation.position.z);
        Direction direction = rotation.lookOrientation;
        Vector3f rotationVec = new Vector3f(direction.pitch, direction.yaw, direction.roll);

        boolean positionChanged = !sameVector(position, voiceChatComponent.currentPosition);
        if (positionChanged) {
            voiceChatComponent.currentPosition = position.clone();
            voiceChatComponent.markPositionDirty = true;
        }

        boolean rotationChanged = !sameVector(rotationVec, voiceChatComponent.currentRotation);
        if (rotationChanged) {
            voiceChatComponent.currentRotation = rotationVec.clone();
            voiceChatComponent.markRotationDirty = true;
        }

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

    private boolean sameVector(Vector3d left, Vector3d right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private boolean sameVector(Vector3f left, Vector3f right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }
}
