package dev.lanshun.voiceguard.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.labymod.addons.voicechat.api.channel.ChannelUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUserRegistry;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.world.ClientWorld;

/** Resolves player names and builds the listings shown by the command. */
final class PlayerDirectory {
  private static final int SNAPSHOT_ATTEMPTS = 3;

  private final VoiceGuardHost host;
  private final VoiceChatBridge bridge;
  private final ConfigStore store;

  PlayerDirectory(VoiceGuardHost host, VoiceChatBridge bridge, ConfigStore store) {
    this.host = host;
    this.bridge = bridge;
    this.store = store;
  }

  List<PlayerEntry> audiblePlayers() {
    return this.collect(false);
  }

  List<PlayerEntry> autoMutedPlayers() {
    List<PlayerEntry> entries = new ArrayList<>();
    Set<UUID> managed = this.store.config().managedMutes().get();
    for (PlayerEntry entry : this.collect(true)) {
      if (managed.contains(entry.uniqueId())) {
        entries.add(entry);
      }
    }

    return entries;
  }

  Set<UUID> presentPlayers() {
    Set<UUID> present = new HashSet<>();
    VoiceUserRegistry registry = this.bridge.userRegistry();
    if (registry == null) {
      return present;
    }

    try {
      for (VoiceUser voiceUser : registry.getAll()) {
        if (voiceUser != null && voiceUser.getUniqueId() != null) {
          present.add(voiceUser.getUniqueId());
        }
      }
    } catch (Throwable throwable) {
      this.host.logError("Could not walk the voice chat user list.", throwable);
    }

    return present;
  }

  String nameOf(UUID uniqueId) {
    ChannelUser channelUser = this.bridge.channelUser(uniqueId);
    if (channelUser != null) {
      try {
        String name = channelUser.getName();
        if (name != null && !name.isEmpty()) {
          return name;
        }
      } catch (Throwable throwable) {
        return "";
      }
    }

    try {
      ClientWorld world = Laby.labyAPI().minecraft().clientWorld();
      if (world != null) {
        Optional<Player> player = world.getPlayer(uniqueId);
        if (player.isPresent()) {
          String name = player.get().getName();
          if (name != null && !name.isEmpty()) {
            return name;
          }
        }
      }
    } catch (Throwable throwable) {
      return "";
    }

    return "";
  }

  void backfillNames() {
    Map<UUID, String> allowlist = this.store.config().allowlist().get();
    if (allowlist == null || allowlist.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, String> entry : allowlist.entrySet()) {
      String stored = entry.getValue();
      if (stored != null && !stored.isEmpty()) {
        continue;
      }

      String resolved = this.nameOf(entry.getKey());
      if (!resolved.isEmpty()) {
        entry.setValue(resolved);
        this.store.markDirty();
      }
    }
  }

  /** Lists from VoiceChat's registry, not the world, which holds every server player. */
  private List<PlayerEntry> collect(boolean muted) {
    List<PlayerEntry> entries = new ArrayList<>();
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    VoiceUserRegistry registry = this.bridge.userRegistry();
    if (volumes == null || registry == null) {
      return entries;
    }

    Set<UUID> seen = new HashSet<>();
    try {
      for (VoiceUser voiceUser : registry.getAll()) {
        if (voiceUser == null || voiceUser.isClient()) {
          continue;
        }

        UUID uniqueId = voiceUser.getUniqueId();
        if (uniqueId == null || !seen.add(uniqueId)) {
          continue;
        }

        Float volume = volumes.get(uniqueId);
        if ((volume != null && volume <= 0.0F) != muted) {
          continue;
        }

        String name = this.nameOf(uniqueId);
        if (!name.isEmpty()) {
          entries.add(new PlayerEntry(uniqueId, name));
        }
      }
    } catch (Throwable throwable) {
      this.host.logError("Could not build the voice chat listing.", throwable);
    }

    sortByName(entries);
    return entries;
  }

  private static void sortByName(List<PlayerEntry> entries) {
    entries.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
  }
}
