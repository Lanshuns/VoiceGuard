package dev.lanshun.voiceguard.core;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.labymod.addons.voicechat.api.VoiceChat;
import net.labymod.addons.voicechat.api.channel.ChannelController;
import net.labymod.addons.voicechat.api.channel.ChannelUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUserRegistry;
import net.labymod.addons.voicechat.api.configuration.VoiceChatConfiguration;
import net.labymod.api.Laby;
import net.labymod.api.addon.LoadedAddon;

/** Guarded access to the official VoiceChat addon. */
public class VoiceChatBridge {
  private static final String VOICECHAT = "voicechat";
  private static final long RETRY_INTERVAL_MILLIS = 5000L;

  private volatile VoiceChat voiceChat;
  private volatile long nextAttempt;

  public VoiceChat voiceChat() {
    if (this.voiceChat != null) {
      return this.voiceChat;
    }

    long now = System.currentTimeMillis();
    if (now < this.nextAttempt) {
      return null;
    }

    this.nextAttempt = now + RETRY_INTERVAL_MILLIS;

    try {
      Optional<LoadedAddon> loadedAddon = Laby.labyAPI().addonService().getAddon(VOICECHAT);
      if (loadedAddon.isEmpty()) {
        return null;
      }

      Object instance = loadedAddon.get().getInstance();
      if (instance instanceof VoiceChat) {
        this.voiceChat = (VoiceChat) instance;
      }
    } catch (Throwable throwable) {
      return null;
    }

    return this.voiceChat;
  }

  public boolean isAvailable() {
    return this.voiceChat() != null;
  }

  public VoiceChatConfiguration configuration() {
    VoiceChat voiceChat = this.voiceChat();
    return voiceChat == null ? null : voiceChat.configuration();
  }

  /** The live per player volume map, which is the same map VoiceChat's mute button writes to. */
  @SuppressWarnings("unchecked")
  public Map<UUID, Float> playerVolumes() {
    VoiceChatConfiguration configuration = this.configuration();
    if (configuration == null) {
      return null;
    }

    Object volumes = configuration.playerVolumes().get();
    return volumes instanceof Map ? (Map<UUID, Float>) volumes : null;
  }

  public VoiceUserRegistry userRegistry() {
    VoiceChat voiceChat = this.voiceChat();
    return voiceChat == null ? null : voiceChat.referenceStorage().voiceUserRegistry();
  }

  public ChannelController channelController() {
    VoiceChat voiceChat = this.voiceChat();
    return voiceChat == null ? null : voiceChat.referenceStorage().channelController();
  }

  public boolean isInCustomChannel() {
    ChannelController controller = this.channelController();
    if (controller == null) {
      return false;
    }

    try {
      return controller.isInCustomChannel();
    } catch (Throwable throwable) {
      return false;
    }
  }

  public UUID currentChannelId() {
    ChannelController controller = this.channelController();
    if (controller == null) {
      return null;
    }

    try {
      return controller.getCurrentChannelId();
    } catch (Throwable throwable) {
      return null;
    }
  }

  public ChannelUser channelUser(UUID uniqueId) {
    ChannelController controller = this.channelController();
    if (controller == null) {
      return null;
    }

    try {
      return controller.index().getChannelUserById(uniqueId);
    } catch (Throwable throwable) {
      return null;
    }
  }
}
