package dev.lanshun.voiceguard.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.labymod.addons.voicechat.api.channel.ChannelUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUserRegistry;
import net.labymod.api.Laby;
import net.labymod.api.labyconnect.LabyConnect;
import net.labymod.api.labyconnect.LabyConnectSession;

/** Mutes by writing VoiceChat's own playerVolumes map, exactly like its mute button. */
final class MuteEngine {
  private final VoiceGuardHost host;
  private final VoiceChatBridge bridge;
  private final ConfigStore store;
  private final PlayerDirectory directory;

  private final Set<UUID> autoMuted = new HashSet<>();
  private final Set<UUID> sessionAllowed = new HashSet<>();
  private boolean startupCleaned;

  MuteEngine(VoiceGuardHost host, VoiceChatBridge bridge, ConfigStore store,
      PlayerDirectory directory) {
    this.host = host;
    this.bridge = bridge;
    this.store = store;
    this.directory = directory;
  }

  void guard(UUID uniqueId, boolean isClient) {
    if (uniqueId == null || isClient) {
      return;
    }

    VoiceGuardConfiguration configuration = this.store.config();
    if (!configuration.enabled().get() || !configuration.muteByDefault().get()) {
      return;
    }

    if (this.autoMuted.contains(uniqueId) || this.sessionAllowed.contains(uniqueId)) {
      return;
    }

    if (configuration.allowlist().get().containsKey(uniqueId)) {
      return;
    }

    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes == null) {
      return;
    }

    Float current = volumes.get(uniqueId);
    if (current != null) {
      if (current > 0.0F) {
        this.sessionAllowed.add(uniqueId);
      }
      return;
    }

    if (this.isExemptFriend(uniqueId)) {
      this.sessionAllowed.add(uniqueId);
      return;
    }

    volumes.put(uniqueId, 0.0F);
    this.autoMuted.add(uniqueId);
    configuration.managedMutes().get().add(uniqueId);
    this.store.markDirty();
  }

  void guard(ChannelUser channelUser) {
    if (channelUser == null) {
      return;
    }

    try {
      if (channelUser.isClient() || channelUser.gameUser() == null) {
        return;
      }

      this.guard(channelUser.gameUser().getUniqueId(), false);
    } catch (Throwable throwable) {
      this.host.logError("Could not read a voice channel entry.", throwable);
    }
  }

  void releaseStaleOnce(Map<UUID, Float> volumes) {
    if (this.startupCleaned) {
      return;
    }

    this.startupCleaned = true;

    Set<UUID> stale = this.store.config().managedMutes().get();
    if (stale.isEmpty()) {
      return;
    }

    for (UUID uniqueId : stale) {
      removeIfMuted(volumes, uniqueId);
    }

    stale.clear();
    this.store.markDirty();
  }

  void reconcile(Map<UUID, Float> volumes, Set<UUID> present) {
    Iterator<UUID> iterator = this.autoMuted.iterator();
    while (iterator.hasNext()) {
      UUID uniqueId = iterator.next();
      Float volume = volumes.get(uniqueId);
      if (volume != null && volume <= 0.0F) {
        continue;
      }

      iterator.remove();
      this.store.config().managedMutes().get().remove(uniqueId);
      this.store.markDirty();

      // A raised or reset entry while they are present is the user's decision. A vanished entry
      // for an absent player is VoiceChat pruning, so they are simply forgotten and will be
      // muted again on sight.
      if (volume != null || present.contains(uniqueId)) {
        this.allow(uniqueId);
      }
    }

    VoiceUserRegistry registry = this.bridge.userRegistry();
    if (registry != null) {
      try {
        for (VoiceUser voiceUser : registry.getAll()) {
          if (voiceUser != null) {
            this.guard(voiceUser.getUniqueId(), voiceUser.isClient());
          }
        }
      } catch (Throwable throwable) {
        this.host.logError("Could not walk the voice chat user list.", throwable);
      }
    }
  }

  void releaseAll() {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes != null) {
      for (UUID uniqueId : this.autoMuted) {
        removeIfMuted(volumes, uniqueId);
      }
    }

    if (!this.autoMuted.isEmpty()) {
      this.store.markDirty();
    }

    this.autoMuted.clear();
    this.store.config().managedMutes().get().clear();
  }

  void onFriendAdded(UUID uniqueId) {
    if (uniqueId == null || !this.store.config().exemptFriends().get()) {
      return;
    }

    if (!this.autoMuted.contains(uniqueId)) {
      return;
    }

    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes == null) {
      return;
    }

    removeIfMuted(volumes, uniqueId);
    this.autoMuted.remove(uniqueId);
    this.store.config().managedMutes().get().remove(uniqueId);
    this.sessionAllowed.add(uniqueId);
    this.store.markDirty();
  }

  private void allow(UUID uniqueId) {
    this.sessionAllowed.add(uniqueId);
    this.store.config().allowlist().get().put(uniqueId, this.directory.nameOf(uniqueId));
  }

  /** Removes an entry only when it is actually a mute, never a volume the user chose. */
  private static void removeIfMuted(Map<UUID, Float> volumes, UUID uniqueId) {
    Float volume = volumes.get(uniqueId);
    if (volume != null && volume <= 0.0F) {
      volumes.remove(uniqueId);
    }
  }

  private boolean isExemptFriend(UUID uniqueId) {
    return this.store.config().exemptFriends().get() && this.isFriend(uniqueId);
  }

  boolean isFriend(UUID uniqueId) {
    try {
      LabyConnect labyConnect = Laby.labyAPI().labyConnect();
      if (labyConnect != null) {
        LabyConnectSession session = labyConnect.getSession();
        if (session != null && session.getFriend(uniqueId) != null) {
          return true;
        }
      }
    } catch (Throwable throwable) {
      this.host.logError("Could not read the LabyConnect friend list.", throwable);
    }

    ChannelUser channelUser = this.bridge.channelUser(uniqueId);
    if (channelUser == null) {
      return false;
    }

    try {
      return channelUser.isFriend();
    } catch (Throwable throwable) {
      return false;
    }
  }
}
