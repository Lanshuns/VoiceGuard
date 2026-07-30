package dev.lanshun.voiceguard.core.ui;

import dev.lanshun.voiceguard.core.AutoMuteService;
import java.util.UUID;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.entity.player.interaction.AbstractBulletPoint;

/** A one click mute toggle in the player interaction menu reached by middle clicking somebody. */
public class VoiceGuardBulletPoint extends AbstractBulletPoint {

  private final AutoMuteService autoMuteService;
  private final boolean unmuteAction;

  public VoiceGuardBulletPoint(AutoMuteService autoMuteService, boolean unmuteAction) {
    super(Component.translatable(
        unmuteAction ? "voiceguard.interaction.unmute" : "voiceguard.interaction.mute"));
    this.autoMuteService = autoMuteService;
    this.unmuteAction = unmuteAction;
  }

  /** Shows the unmute entry only for muted players and the mute entry only for audible ones. */
  @Override
  public boolean isVisible(Player player) {
    UUID uniqueId = uniqueIdOf(player);
    if (uniqueId == null || !this.autoMuteService.isVoiceChatAvailable()) {
      return false;
    }

    return this.autoMuteService.isMuted(uniqueId) == this.unmuteAction;
  }

  /** Applies the entry's action to the player. */
  @Override
  public void execute(Player player) {
    UUID uniqueId = uniqueIdOf(player);
    if (uniqueId == null) {
      return;
    }

    if (this.unmuteAction) {
      this.autoMuteService.unmute(uniqueId);
    } else {
      this.autoMuteService.mute(uniqueId);
    }
  }

  /** Reads the player id, tolerating a partially initialised entity. */
  private static UUID uniqueIdOf(Player player) {
    try {
      return player == null ? null : player.getUniqueId();
    } catch (Throwable throwable) {
      return null;
    }
  }
}
