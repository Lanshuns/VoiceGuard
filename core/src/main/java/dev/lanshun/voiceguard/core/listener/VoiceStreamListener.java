package dev.lanshun.voiceguard.core.listener;

import dev.lanshun.voiceguard.core.AutoMuteService;
import net.labymod.addons.voicechat.api.event.channel.UserShowEvent;
import net.labymod.addons.voicechat.api.event.stream.AudioStreamStartEvent;
import net.labymod.api.event.Subscribe;

/** Applies the default mute at the two moments that matter. */
public class VoiceStreamListener {

  private final AutoMuteService autoMuteService;

  public VoiceStreamListener(AutoMuteService autoMuteService) {
    this.autoMuteService = autoMuteService;
  }

  /** Mutes a speaker before their first audio frame is queued. */
  @Subscribe
  public void onAudioStreamStart(AudioStreamStartEvent event) {
    this.autoMuteService.guard(event.voiceUser());
  }

  /**
   * Mutes a player as they appear in the channel list, which is well before they can talk, so
   * VoiceChat's own panel already shows them muted when the user opens it.
   */
  @Subscribe
  public void onUserShow(UserShowEvent event) {
    this.autoMuteService.guard(event.user());
  }
}
