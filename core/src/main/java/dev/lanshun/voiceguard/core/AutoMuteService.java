package dev.lanshun.voiceguard.core;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.labymod.addons.voicechat.api.channel.ChannelController;
import net.labymod.addons.voicechat.api.channel.ChannelUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUserRegistry;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.labyconnect.LabyConnect;
import net.labymod.api.labyconnect.LabyConnectSession;

/** Keeps every voice chat participant muted unless the user has said otherwise. */
public class AutoMuteService {

  private static final int RELEASE_MARGIN = 6;
  private static final int SNAPSHOT_ATTEMPTS = 3;

  private final VoiceGuardHost addon;
  private final VoiceChatBridge bridge;
  private final Set<UUID> autoMuted = new HashSet<>();
  private final Set<UUID> sessionAllowed = new HashSet<>();

  private boolean startupCleaned;
  private boolean dirty;
  private boolean microphoneGuarded;
  private boolean microphoneOverridden;

  public AutoMuteService(VoiceGuardHost addon, VoiceChatBridge bridge) {
    this.addon = addon;
    this.bridge = bridge;
  }

  /** Mutes the given player unless they are exempt. */
  public synchronized void guard(UUID uniqueId, boolean isClient) {
    if (uniqueId == null || isClient) {
      return;
    }

    VoiceGuardConfiguration configuration = this.addon.configuration();
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
    this.dirty = true;
  }

  /** Guards the player behind a voice chat user. */
  public void guard(VoiceUser voiceUser) {
    if (voiceUser != null) {
      this.guard(voiceUser.getUniqueId(), voiceUser.isClient());
    }
  }

  /** Guards the player behind a voice channel entry, which carries no id of its own. */
  public void guard(ChannelUser channelUser) {
    if (channelUser == null) {
      return;
    }

    try {
      if (channelUser.isClient() || channelUser.gameUser() == null) {
        return;
      }

      this.guard(channelUser.gameUser().getUniqueId(), false);
    } catch (Throwable throwable) {
      this.addon.logError("Could not read a voice channel entry.", throwable);
    }
  }

  /**
   * Runs the work that cannot be driven by events: clearing mutes left by a previous session,
   * noticing unmutes made through VoiceChat's own panel, catching players who became audible
   * without firing a subscribed event, resolving missing names and updating the microphone guard.
   */
  public synchronized void sweep() {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes == null) {
      return;
    }

    if (!this.startupCleaned) {
      this.startupCleaned = true;
      this.releaseStale(volumes);
    }

    VoiceGuardConfiguration configuration = this.addon.configuration();
    if (!configuration.enabled().get()) {
      this.releaseEverything();
      this.flush();
      return;
    }

    if (!configuration.muteByDefault().get()) {
      this.releaseAll();
      this.updateMicrophoneGuard();
      this.flush();
      return;
    }

    Iterator<UUID> iterator = this.autoMuted.iterator();
    while (iterator.hasNext()) {
      UUID uniqueId = iterator.next();
      Float volume = volumes.get(uniqueId);
      if (volume == null || volume > 0.0F) {
        iterator.remove();
        this.addon.configuration().managedMutes().get().remove(uniqueId);
        this.allow(uniqueId);
        this.dirty = true;
      }
    }

    VoiceUserRegistry registry = this.bridge.userRegistry();
    if (registry != null) {
      try {
        for (VoiceUser voiceUser : registry.getAll()) {
          this.guard(voiceUser);
        }
      } catch (Throwable throwable) {
        this.addon.logError("Could not walk the voice chat user list.", throwable);
      }
    }

    this.backfillNames();
    this.reconcileManualMutes(volumes);
    this.updateMicrophoneGuard();
    this.flush();
  }

  /** Drops every mute applied by this addon, leaving the user's own entries untouched. */
  public synchronized void releaseAll() {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes != null) {
      for (UUID uniqueId : this.autoMuted) {
        this.removeIfMuted(volumes, uniqueId);
      }
    }

    if (!this.autoMuted.isEmpty()) {
      this.dirty = true;
    }

    this.autoMuted.clear();
    this.addon.configuration().managedMutes().get().clear();
  }

  /** Releases the addon's player mutes and saves, leaving the microphone guard alone. */
  public synchronized void releaseMutes() {
    try {
      this.releaseAll();
      this.flush();
    } catch (Throwable throwable) {
      this.addon.logError("Could not release the addon's mutes.", throwable);
    }
  }

  /**
   * Undoes everything this addon has done to the client: its player mutes and its hold on the
   * microphone.
   */
  public synchronized void releaseEverything() {
    try {
      this.releaseAll();
      this.releaseMicrophoneGuard();
      this.flush();
    } catch (Throwable throwable) {
      this.addon.logError("Could not release the addon's mutes.", throwable);
    }
  }

  /** Reports a sweep that failed, so one bad tick cannot escape into LabyMod's event dispatch. */
  public void logSweepFailure(Throwable throwable) {
    this.addon.logError("The Voice Guard sweep failed and will run again shortly.", throwable);
  }

  /** Whether VoiceChat is loaded and usable. */
  public boolean isVoiceChatAvailable() {
    return this.bridge.isAvailable();
  }

  /** Whether this player is currently muted, however that mute got there. */
  public synchronized boolean isMuted(UUID uniqueId) {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (uniqueId == null || volumes == null) {
      return false;
    }

    Float volume = volumes.get(uniqueId);
    return volume != null && volume <= 0.0F;
  }

  /**
   * Makes a player audible and remembers the decision, exactly as if the user had raised their
   * slider in VoiceChat's own panel.
   */
  public synchronized void unmute(UUID uniqueId) {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (uniqueId == null || volumes == null) {
      return;
    }

    volumes.put(uniqueId, 1.0F);
    this.autoMuted.remove(uniqueId);
    this.addon.configuration().managedMutes().get().remove(uniqueId);
    this.addon.configuration().manualMuteNames().get().remove(uniqueId);
    this.allow(uniqueId);
    this.dirty = true;
    this.flush();
  }

  /** Mutes a player on the user's own initiative. */
  public synchronized void mute(UUID uniqueId) {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (uniqueId == null || volumes == null) {
      return;
    }

    volumes.put(uniqueId, 0.0F);
    this.autoMuted.remove(uniqueId);
    this.sessionAllowed.remove(uniqueId);
    this.addon.configuration().managedMutes().get().remove(uniqueId);
    this.addon.configuration().allowlist().get().remove(uniqueId);
    String name = this.nameOf(uniqueId);
    if (!name.isEmpty()) {
      this.addon.configuration().manualMuteNames().get().put(uniqueId, name);
    }
    this.dirty = true;
    this.flush();
  }

  /**
   * Releases the mute applied to a player who has just been added as a LabyMod friend, so friending
   * takes effect immediately rather than after a restart.
   */
  public synchronized void onFriendAdded(UUID uniqueId) {
    if (uniqueId == null || !this.addon.configuration().exemptFriends().get()) {
      return;
    }

    if (!this.autoMuted.contains(uniqueId)) {
      return;
    }

    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes == null) {
      return;
    }

    this.removeIfMuted(volumes, uniqueId);
    this.autoMuted.remove(uniqueId);
    this.addon.configuration().managedMutes().get().remove(uniqueId);
    this.sessionAllowed.add(uniqueId);
    this.dirty = true;
    this.flush();
  }

  /** A player as shown in the command listing. */
  public record PlayerEntry(UUID uniqueId, String name) {}

  /** Everyone on this server that the user can currently hear. */
  public synchronized List<PlayerEntry> audiblePlayers() {
    return this.collect(false);
  }

  /** Every player the user has ever chosen to hear, across all servers. */
  public synchronized List<PlayerEntry> allowlistedPlayers() {
    List<PlayerEntry> entries = new ArrayList<>();
    Map<UUID, String> allowlist = this.addon.configuration().allowlist().get();
    if (allowlist == null) {
      return entries;
    }

    for (Map.Entry<UUID, String> entry : allowlist.entrySet()) {
      String name = entry.getValue();
      if (name != null && !name.isEmpty()) {
        entries.add(new PlayerEntry(entry.getKey(), name));
      }
    }

    entries.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
    return entries;
  }

  /** Present players muted by the addon itself. */
  public synchronized List<PlayerEntry> autoMutedPlayers() {
    List<PlayerEntry> entries = new ArrayList<>();
    Set<UUID> managed = this.addon.configuration().managedMutes().get();
    for (PlayerEntry entry : this.collect(true)) {
      if (managed.contains(entry.uniqueId())) {
        entries.add(entry);
      }
    }

    return entries;
  }

  /** Every player the user muted themselves whose name is known, sorted, however long ago. */
  public synchronized List<PlayerEntry> manuallyMutedPlayers() {
    List<PlayerEntry> entries = new ArrayList<>();
    Map<UUID, String> names = this.addon.configuration().manualMuteNames().get();
    if (names == null) {
      return entries;
    }

    for (Map.Entry<UUID, String> entry : names.entrySet()) {
      String name = entry.getValue();
      if (name != null && !name.isEmpty()) {
        entries.add(new PlayerEntry(entry.getKey(), name));
      }
    }

    entries.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
    return entries;
  }

  /**
   * Finds a player by the name shown in the listing, checking the world first and then the
   * allowlist, so somebody unmuted earlier can still be re-muted after they log off.
   */
  public synchronized UUID resolveByName(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }

    try {
      ClientWorld world = Laby.labyAPI().minecraft().clientWorld();
      if (world != null) {
        Optional<Player> player = world.getPlayer(name);
        if (player.isPresent()) {
          return player.get().getUniqueId();
        }
      }
    } catch (Throwable throwable) {
      this.addon.logError("Could not look up a player by name.", throwable);
    }

    UUID fromAllowlist = findByName(this.addon.configuration().allowlist().get(), name);
    if (fromAllowlist != null) {
      return fromAllowlist;
    }

    return findByName(this.addon.configuration().manualMuteNames().get(), name);
  }

  /** Finds a player id by display name in a persisted name map. */
  private static UUID findByName(Map<UUID, String> names, String name) {
    if (names != null) {
      for (Map.Entry<UUID, String> entry : names.entrySet()) {
        if (name.equalsIgnoreCase(entry.getValue())) {
          return entry.getKey();
        }
      }
    }

    return null;
  }

  /** What the microphone guard wants to do next. */
  public enum MicrophoneAction {
    NONE,
    MUTE,
    UNMUTE
  }

  /** Advances the microphone guard and returns what should happen to the microphone. */
  public synchronized MicrophoneAction decideMicrophone(
      boolean guardEnabled, boolean inEarshot, boolean inputMuted) {

    if (!guardEnabled || !inEarshot) {
      this.microphoneOverridden = false;

      if (this.microphoneGuarded) {
        this.microphoneGuarded = false;
        return inputMuted ? MicrophoneAction.UNMUTE : MicrophoneAction.NONE;
      }

      return MicrophoneAction.NONE;
    }

    if (this.microphoneOverridden) {
      return MicrophoneAction.NONE;
    }

    if (!this.microphoneGuarded) {
      if (inputMuted) {
        return MicrophoneAction.NONE;
      }

      this.microphoneGuarded = true;
      return MicrophoneAction.MUTE;
    }

    if (!inputMuted) {
      this.microphoneGuarded = false;
      this.microphoneOverridden = true;
    }

    return MicrophoneAction.NONE;
  }

  /** Applies the microphone guard's decision to VoiceChat. */
  private void updateMicrophoneGuard() {
    ChannelController controller = this.bridge.channelController();
    if (controller == null) {
      return;
    }

    VoiceGuardConfiguration configuration = this.addon.configuration();
    boolean guardEnabled = configuration.enabled().get() && configuration.microphoneGuard().get();

    MicrophoneAction action = this.decideMicrophone(
        guardEnabled,
        guardEnabled && this.mutedListenerInEarshot(),
        controller.isInputMuted());

    if (action == MicrophoneAction.MUTE) {
      controller.setInputMuted(true);
    } else if (action == MicrophoneAction.UNMUTE) {
      controller.setInputMuted(false);
    }
  }

  /** Stands the guard down and restores the microphone if this addon is holding it. */
  private void releaseMicrophoneGuard() {
    ChannelController controller = this.bridge.channelController();
    if (controller == null) {
      this.microphoneGuarded = false;
      return;
    }

    if (this.decideMicrophone(false, false, controller.isInputMuted())
        == MicrophoneAction.UNMUTE) {
      controller.setInputMuted(false);
    }
  }

  /** Whether a muted player is close enough to hear the user. */
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
      this.addon.logError("Could not evaluate the voice chat user list.", throwable);
    }

    return false;
  }

  /** Whether the player is within the configured radius, measured in real block distance. */
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

      int radius = Math.max(1, this.addon.configuration().guardRadius().get());
      int effective = this.microphoneGuarded ? radius + RELEASE_MARGIN : radius;
      return self.getDistanceSquared(other.get()) <= (double) effective * effective;
    } catch (Throwable throwable) {
      return false;
    }
  }

  /**
   * Builds one side of the listing from VoiceChat's user registry rather than from the world, since
   * the world contains every player on the server and most of them do not run LabyMod.
   */
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
      this.addon.logError("Could not build the voice chat listing.", throwable);
    }

    entries.sort((first, second) -> first.name().compareToIgnoreCase(second.name()));
    return entries;
  }

  /** Clears mutes recorded by a previous session that was closed or crashed before cleaning up. */
  private void releaseStale(Map<UUID, Float> volumes) {
    Set<UUID> stale = this.addon.configuration().managedMutes().get();
    if (stale.isEmpty()) {
      return;
    }

    for (UUID uniqueId : stale) {
      this.removeIfMuted(volumes, uniqueId);
    }

    stale.clear();
    this.dirty = true;
  }

  /** Removes an entry only when it is actually a mute, never a volume the user chose. */
  private void removeIfMuted(Map<UUID, Float> volumes, UUID uniqueId) {
    Float volume = volumes.get(uniqueId);
    if (volume != null && volume <= 0.0F) {
      volumes.remove(uniqueId);
    }
  }

  /** Records that the user wants to hear this player, persisting it unless asked not to. */
  private void allow(UUID uniqueId) {
    this.sessionAllowed.add(uniqueId);

    VoiceGuardConfiguration configuration = this.addon.configuration();
    if (configuration.rememberUnmuted().get()) {
      configuration.allowlist().get().put(uniqueId, this.nameOf(uniqueId));
    }
  }

  /** Saves the configuration if anything changed since the last save. */
  private void flush() {
    if (!this.dirty) {
      return;
    }

    this.dirty = false;

    try {
      this.addon.saveConfiguration();
    } catch (Throwable throwable) {
      this.addon.logError("Could not save the configuration.", throwable);
    }
  }

  /** Whether this player is exempt because the user has chosen to always hear friends. */
  private boolean isExemptFriend(UUID uniqueId) {
    return this.addon.configuration().exemptFriends().get() && this.isFriend(uniqueId);
  }

  /** Whether this player is a LabyMod friend. */
  private boolean isFriend(UUID uniqueId) {
    try {
      LabyConnect labyConnect = Laby.labyAPI().labyConnect();
      if (labyConnect != null) {
        LabyConnectSession session = labyConnect.getSession();
        if (session != null && session.getFriend(uniqueId) != null) {
          return true;
        }
      }
    } catch (Throwable throwable) {
      this.addon.logError("Could not read the LabyConnect friend list.", throwable);
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

  /** Best effort display name. */
  private String nameOf(UUID uniqueId) {
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

  /**
   * Keeps the manual mute name map in step with VoiceChat's volume map: records names for hand
   * muted players while they are nearby to be asked, and drops entries whose mute is gone, which
   * happens when the user unmutes through VoiceChat's own panel.
   */
  private void reconcileManualMutes(Map<UUID, Float> volumes) {
    Map<UUID, String> names = this.addon.configuration().manualMuteNames().get();
    if (names == null) {
      return;
    }

    Map<UUID, Float> snapshot = snapshot(volumes);
    if (snapshot == null) {
      return;
    }

    Set<UUID> managed = this.addon.configuration().managedMutes().get();
    for (Map.Entry<UUID, Float> entry : snapshot.entrySet()) {
      UUID uniqueId = entry.getKey();
      if (entry.getValue() > 0.0F || managed.contains(uniqueId) || names.containsKey(uniqueId)) {
        continue;
      }

      String name = this.nameOf(uniqueId);
      if (!name.isEmpty()) {
        names.put(uniqueId, name);
        this.dirty = true;
      }
    }

    Iterator<UUID> iterator = names.keySet().iterator();
    while (iterator.hasNext()) {
      UUID uniqueId = iterator.next();
      Float volume = snapshot.get(uniqueId);
      if (volume == null || volume > 0.0F) {
        iterator.remove();
        this.dirty = true;
      }
    }
  }

  /**
   * Copies VoiceChat's volume map so it can be read safely, since VoiceChat writes to it from its
   * own audio thread and a plain iteration can fail part way through.
   */
  private static Map<UUID, Float> snapshot(Map<UUID, Float> volumes) {
    for (int attempt = 0; attempt < SNAPSHOT_ATTEMPTS; attempt++) {
      try {
        return new HashMap<>(volumes);
      } catch (ConcurrentModificationException exception) {
        continue;
      }
    }

    return null;
  }

  /** Fills in names for allowlist entries saved before a name could be resolved. */
  private void backfillNames() {
    Map<UUID, String> allowlist = this.addon.configuration().allowlist().get();
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
        this.dirty = true;
      }
    }
  }
}
