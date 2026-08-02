package dev.lanshun.voiceguard.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.labymod.addons.voicechat.api.channel.ChannelUser;
import net.labymod.addons.voicechat.api.client.user.VoiceUser;

/** Facade and single synchronization boundary for everything the addon does to the client. */
public class AutoMuteService {
  private final VoiceGuardHost host;
  private final VoiceChatBridge bridge;
  private final ConfigStore store;
  private final PlayerDirectory directory;
  private final MuteEngine engine;
  private final MicrophoneGuard microphoneGuard;

  public AutoMuteService(VoiceGuardHost host, VoiceChatBridge bridge) {
    this.host = host;
    this.bridge = bridge;
    this.store = new ConfigStore(host);
    this.directory = new PlayerDirectory(host, bridge, this.store);
    this.engine = new MuteEngine(host, bridge, this.store, this.directory);
    this.microphoneGuard = new MicrophoneGuard(host, bridge, this.store);
  }

  public synchronized void guard(UUID uniqueId, boolean isClient) {
    this.engine.guard(uniqueId, isClient);
  }

  public synchronized void guard(VoiceUser voiceUser) {
    if (voiceUser != null) {
      this.engine.guard(voiceUser.getUniqueId(), voiceUser.isClient());
    }
  }

  public synchronized void guard(ChannelUser channelUser) {
    this.engine.guard(channelUser);
  }

  /** Periodic catch-all for everything the events cannot drive. */
  public synchronized void sweep() {
    Map<UUID, Float> volumes = this.bridge.playerVolumes();
    if (volumes == null) {
      return;
    }

    this.engine.releaseStaleOnce(volumes);

    VoiceGuardConfiguration configuration = this.host.configuration();
    if (!configuration.enabled().get()) {
      this.releaseEverything();
      return;
    }

    if (!configuration.muteByDefault().get()) {
      this.engine.releaseAll();
      this.microphoneGuard.update();
      this.store.flush();
      return;
    }

    Set<UUID> present = this.directory.presentPlayers();
    this.engine.reconcile(volumes, present);
    this.directory.backfillNames();
    this.microphoneGuard.update();
    this.store.flush();
  }

  public synchronized void releaseAll() {
    this.engine.releaseAll();
  }

  public synchronized void releaseMutes() {
    try {
      this.engine.releaseAll();
      this.store.flush();
    } catch (Throwable throwable) {
      this.host.logError("Could not release the addon's mutes.", throwable);
    }
  }

  public synchronized void releaseEverything() {
    try {
      this.engine.releaseAll();
      this.microphoneGuard.release();
      this.store.flush();
    } catch (Throwable throwable) {
      this.host.logError("Could not release the addon's mutes.", throwable);
    }
  }

  /** Reports a sweep that failed, so one bad tick cannot escape into LabyMod's event dispatch. */
  public void logSweepFailure(Throwable throwable) {
    this.host.logError("The Voice Guard sweep failed and will run again shortly.", throwable);
  }

  public boolean isVoiceChatAvailable() {
    return this.bridge.isAvailable();
  }

  public synchronized void onFriendAdded(UUID uniqueId) {
    this.engine.onFriendAdded(uniqueId);
    this.store.flush();
  }

  public synchronized List<PlayerEntry> audiblePlayers() {
    return this.directory.audiblePlayers();
  }

  public synchronized List<PlayerEntry> autoMutedPlayers() {
    return this.directory.autoMutedPlayers();
  }

  public synchronized boolean isFriend(UUID uniqueId) {
    return uniqueId != null && this.engine.isFriend(uniqueId);
  }

}
