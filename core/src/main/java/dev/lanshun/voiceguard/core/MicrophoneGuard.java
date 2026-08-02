package dev.lanshun.voiceguard.core;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.labymod.addons.voicechat.api.channel.ChannelController;
import net.labymod.addons.voicechat.api.client.user.VoiceUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUserRegistry;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.world.ClientWorld;

/** Cuts the user's microphone while a muted player is in earshot. */
final class MicrophoneGuard {
  /** Extra release distance so a player on the boundary cannot toggle the microphone. */
  private static final int RELEASE_MARGIN = 6;

  private final VoiceGuardHost host;
  private final VoiceChatBridge bridge;
  private final ConfigStore store;

  private boolean holding;
  private boolean overridden;

  MicrophoneGuard(VoiceGuardHost host, VoiceChatBridge bridge, ConfigStore store) {
    this.host = host;
    this.bridge = bridge;
    this.store = store;
  }

  private MicrophoneAction decide(boolean guardEnabled, boolean inEarshot, boolean inputMuted) {
    if (!guardEnabled || !inEarshot) {
      this.overridden = false;

      if (this.holding) {
        this.holding = false;
        return inputMuted ? MicrophoneAction.UNMUTE : MicrophoneAction.NONE;
      }

      return MicrophoneAction.NONE;
    }

    if (this.overridden) {
      return MicrophoneAction.NONE;
    }

    if (!this.holding) {
      if (inputMuted) {
        return MicrophoneAction.NONE;
      }

      this.holding = true;
      return MicrophoneAction.MUTE;
    }

    if (!inputMuted) {
      this.holding = false;
      this.overridden = true;
    }

    return MicrophoneAction.NONE;
  }

  void update() {
    ChannelController controller = this.bridge.channelController();
    if (controller == null) {
      return;
    }

    VoiceGuardConfiguration configuration = this.store.config();
    boolean guardEnabled = configuration.enabled().get() && configuration.microphoneGuard().get();

    MicrophoneAction action = this.decide(
        guardEnabled,
        guardEnabled && this.mutedListenerInEarshot(),
        controller.isInputMuted());

    if (action == MicrophoneAction.MUTE) {
      controller.setInputMuted(true);
    } else if (action == MicrophoneAction.UNMUTE) {
      controller.setInputMuted(false);
    }
  }

  void release() {
    ChannelController controller = this.bridge.channelController();
    if (controller == null) {
      this.holding = false;
      return;
    }

    if (this.decide(false, false, controller.isInputMuted()) == MicrophoneAction.UNMUTE) {
      controller.setInputMuted(false);
    }
  }

  private boolean mutedListenerInEarshot() {
    VoiceUserRegistry registry = this.bridge.userRegistry();
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (registry == null || volumes == null) {
      return false;
    }

    UUID currentChannel = this.bridge.currentChannelId();
    boolean inCustomChannel = this.bridge.isInCustomChannel();

    try {
      for (VoiceUser voiceUser : registry.getAll()) {
        if (voiceUser == null || voiceUser.isClient()) {
          continue;
        }

        UUID uniqueId = voiceUser.getUniqueId();
        if (uniqueId == null) {
          continue;
        }

        Float volume = volumes.get(uniqueId);
        if (volume == null || volume > 0.0F) {
          continue;
        }

        if (inCustomChannel
            && currentChannel != null
            && currentChannel.equals(voiceUser.getChannelId())) {
          return true;
        }

        if (this.isWithinGuardRadius(uniqueId)) {
          return true;
        }
      }
    } catch (Throwable throwable) {
      this.host.logError("Could not evaluate the voice chat user list.", throwable);
    }

    return false;
  }

  private boolean isWithinGuardRadius(UUID uniqueId) {
    try {
      Minecraft minecraft = Laby.labyAPI().minecraft();
      ClientWorld world = minecraft.clientWorld();
      ClientPlayer self = minecraft.getClientPlayer();
      if (world == null || self == null) {
        return false;
      }

      Optional<Player> other = world.getPlayer(uniqueId);
      if (other.isEmpty()) {
        return false;
      }

      int radius = Math.max(1, this.store.config().guardRadius().get());
      int effective = this.holding ? radius + RELEASE_MARGIN : radius;
      return self.getDistanceSquared(other.get()) <= (double) effective * effective;
    } catch (Throwable throwable) {
      return false;
    }
  }

  private enum MicrophoneAction {
    NONE,
    MUTE,
    UNMUTE
  }
}
