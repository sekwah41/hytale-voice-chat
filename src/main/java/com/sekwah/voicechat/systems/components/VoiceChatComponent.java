package com.sekwah.voicechat.systems.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.Nullable;

public class VoiceChatComponent implements Component<EntityStore> {

    public Vector3d lastSentPosition;

    // For now will send at a hardcoded interval.
    public float timeSinceLastUpdate;

    public VoiceChatComponent(VoiceChatComponent voiceChatComponent) {
        this.lastSentPosition = voiceChatComponent.lastSentPosition;
        this.timeSinceLastUpdate = voiceChatComponent.timeSinceLastUpdate;
    }

    public VoiceChatComponent() {

    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return new VoiceChatComponent(this);
    }
}
