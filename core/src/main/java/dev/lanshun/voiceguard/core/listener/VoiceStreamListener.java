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

  @Subscribe
  public void onAudioStreamStart(AudioStreamStartEvent event) {
    this.autoMuteService.guard(event.voiceUser());
  }

  /** Mutes a player as they appear in the channel list, before they can talk. */
  @Subscribe
  public void onUserShow(UserShowEvent event) {
    this.autoMuteService.guard(event.user());
  }
}
