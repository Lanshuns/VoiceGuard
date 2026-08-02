package dev.lanshun.voiceguard.core.listener;

import dev.lanshun.voiceguard.core.AutoMuteService;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.labymod.labyconnect.session.friend.LabyConnectFriendAddEvent;

/** Makes befriending somebody take effect immediately. */
public class FriendListener {
  private final AutoMuteService autoMuteService;

  public FriendListener(AutoMuteService autoMuteService) {
    this.autoMuteService = autoMuteService;
  }

  @Subscribe
  public void onFriendAdd(LabyConnectFriendAddEvent event) {
    if (event.friend() == null) {
      return;
    }

    this.autoMuteService.onFriendAdded(event.friend().getUniqueId());
  }
}
