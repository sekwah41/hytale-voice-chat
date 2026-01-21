package com.sekwah.voicechat.systems.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.Nullable;

public class VoiceChatComponent implements Component<EntityStore> {

    public Vector3d currentPosition;
    public boolean markPositionDirty;
    public Vector3f currentRotation;
    public boolean markRotationDirty;

    // Will broadcast its locations to other players at a set interval
    // Though players will be updated about their rotation data more frequently
    public float timeSinceLastUpdate;

    public VoiceChatComponent(VoiceChatComponent voiceChatComponent) {
        this.currentPosition = voiceChatComponent.currentPosition;
        this.currentRotation = voiceChatComponent.currentRotation;
        this.markPositionDirty = voiceChatComponent.markPositionDirty;
        this.markRotationDirty = voiceChatComponent.markRotationDirty;
        this.timeSinceLastUpdate = voiceChatComponent.timeSinceLastUpdate;
    }

    public VoiceChatComponent() {

    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return new VoiceChatComponent(this);
    }
}
