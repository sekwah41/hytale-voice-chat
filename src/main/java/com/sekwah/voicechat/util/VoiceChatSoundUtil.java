package com.sekwah.voicechat.util;

import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;

public final class VoiceChatSoundUtil {

    private VoiceChatSoundUtil() {
    }

    public static boolean playUiSound(PlayerRef playerRef, String soundId) {
        if (playerRef == null || soundId == null || soundId.isBlank()) {
            return false;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
        if (soundIndex == -1) {
            return false;
        }
        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI);
        return true;
    }
}
