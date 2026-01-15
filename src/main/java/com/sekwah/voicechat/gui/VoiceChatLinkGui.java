package com.sekwah.voicechat.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;

public class VoiceChatLinkGui extends InteractiveCustomUIPage<VoiceChatLinkGui.VoiceChatLinkData> {

    private final String link;

    public VoiceChatLinkGui(@NonNullDecl PlayerRef playerRef, String link) {
        super(playerRef, CustomPageLifetime.CanDismiss, VoiceChatLinkData.CODEC);
        this.link = link;
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store, @NonNullDecl VoiceChatLinkData data) {
        super.handleDataEvent(ref, store, data);
        if (data.button != null && data.button.equals("Close")) {
                this.close();
                return;
            }

        this.sendUpdate();
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder uiCommandBuilder, @NonNullDecl UIEventBuilder uiEventBuilder, @NonNullDecl Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/Sekwah_VoiceChat_Link.ui");
        uiCommandBuilder.set("#LinkField.Value", this.link);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Button", "Close"), false);
    }

    public static class VoiceChatLinkData {
        static final String KEY_BUTTON = "Button";

        public static final BuilderCodec<VoiceChatLinkData> CODEC = BuilderCodec.<VoiceChatLinkData>builder(VoiceChatLinkData.class, VoiceChatLinkData::new)
                .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING), (data, s) -> data.button = s, data -> data.button)
                .build();

        private String button;
    }
}
