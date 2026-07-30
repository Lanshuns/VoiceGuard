package dev.lanshun.voiceguard.core.listener;

import dev.lanshun.voiceguard.core.AutoMuteService;
import dev.lanshun.voiceguard.core.VoiceGuardHost;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.network.server.ServerAddress;
import net.labymod.api.client.network.server.ServerData;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.event.client.network.server.ServerJoinEvent;

/** Prints a short summary in chat shortly after joining a server. */
public class ServerJoinListener {

  private static final int DELAY_TICKS = 160;

  private final VoiceGuardHost addon;
  private final AutoMuteService autoMuteService;

  private String address;
  private int countdown;

  public ServerJoinListener(VoiceGuardHost addon, AutoMuteService autoMuteService) {
    this.addon = addon;
    this.autoMuteService = autoMuteService;
  }

  /** Records the server that was joined and starts the countdown. */
  @Subscribe
  public void onServerJoin(ServerJoinEvent event) {
    this.address = describe(event.serverData());
    this.countdown = DELAY_TICKS;
  }

  /** Sends the summary once the countdown elapses. */
  @Subscribe
  public void onGameTick(GameTickEvent event) {
    if (event.phase() != Phase.PRE || this.countdown <= 0) {
      return;
    }

    if (--this.countdown > 0) {
      return;
    }

    try {
      this.announce();
    } catch (Throwable throwable) {
      this.addon.logError("Could not send the join summary.", throwable);
    } finally {
      this.address = null;
    }
  }

  /** Builds and sends the summary. */
  private void announce() {
    if (!this.addon.configuration().enabled().get() || !this.autoMuteService.isVoiceChatAvailable()) {
      return;
    }

    this.addon.displayChatMessage(
        Component.translatable("voiceguard.join.title", NamedTextColor.AQUA));

    if (this.address != null && !this.address.isEmpty()) {
      this.addon.displayChatMessage(
          Component.translatable("voiceguard.join.connected", NamedTextColor.GRAY)
              .append(Component.text(" " + this.address, NamedTextColor.WHITE)));
    }

    this.addon.displayChatMessage(
        Component.translatable("voiceguard.join.audible", NamedTextColor.WHITE)
            .append(Component.text(
                " " + this.autoMuteService.audiblePlayers().size(), NamedTextColor.GREEN)));

    this.addon.displayChatMessage(
        Component.translatable("voiceguard.join.autoMuted", NamedTextColor.WHITE)
            .append(Component.text(
                " " + this.autoMuteService.autoMutedPlayers().size(), NamedTextColor.RED)));

    this.addon.displayChatMessage(
        Component.translatable("voiceguard.join.detailsBefore", NamedTextColor.GRAY)
            .append(Component.text(" '/vg' ", NamedTextColor.WHITE))
            .append(Component.translatable("voiceguard.join.detailsAfter", NamedTextColor.GRAY)));
  }

  /** The server address as the user would recognise it, host and port only when non standard. */
  private static String describe(ServerData serverData) {
    try {
      if (serverData == null) {
        return "";
      }

      ServerAddress serverAddress = serverData.address();
      if (serverAddress == null) {
        return "";
      }

      String host = serverAddress.getHost();
      if (host == null || host.isEmpty()) {
        return "";
      }

      int port = serverAddress.getPort();
      return port == 25565 ? host : host + ":" + port;
    } catch (Throwable throwable) {
      return "";
    }
  }
}
