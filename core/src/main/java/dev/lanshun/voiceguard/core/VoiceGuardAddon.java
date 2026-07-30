package dev.lanshun.voiceguard.core;

import dev.lanshun.voiceguard.core.commands.VoiceGuardCommand;
import dev.lanshun.voiceguard.core.listener.FriendListener;
import dev.lanshun.voiceguard.core.listener.ServerJoinListener;
import dev.lanshun.voiceguard.core.listener.VoiceStreamListener;
import dev.lanshun.voiceguard.core.listener.VoiceSweepListener;
import dev.lanshun.voiceguard.core.ui.VoiceGuardBulletPoint;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.interaction.InteractionMenuRegistry;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.ShutdownEvent;
import net.labymod.api.models.addon.annotation.AddonMain;

/**
 * Inverts the default of LabyMod's VoiceChat addon, so that every participant starts muted and the
 * user unmutes only the people they want to hear.
 */
@AddonMain
public class VoiceGuardAddon extends LabyAddon<VoiceGuardConfiguration> implements VoiceGuardHost {

  private AutoMuteService autoMuteService;

  /** Registers the hooks. */
  @Override
  protected void enable() {
    this.registerSettingCategory();
    this.autoMuteService = new AutoMuteService(this, new VoiceChatBridge());
    this.registerReleaseOnDisable();

    try {
      this.registerListener(new VoiceStreamListener(this.autoMuteService));
      this.registerListener(new VoiceSweepListener(this.autoMuteService));
      this.registerListener(new FriendListener(this.autoMuteService));
      this.registerListener(new ServerJoinListener(this, this.autoMuteService));
      this.registerCommand(new VoiceGuardCommand(this.autoMuteService));

      InteractionMenuRegistry interactionMenu = this.labyAPI().interactionMenuRegistry();
      interactionMenu.register(
          "voiceguard_unmute", new VoiceGuardBulletPoint(this.autoMuteService, true));
      interactionMenu.register(
          "voiceguard_mute", new VoiceGuardBulletPoint(this.autoMuteService, false));
    } catch (Throwable throwable) {
      this.logger().error(
          "Voice Guard could not hook into the VoiceChat addon and will stay inactive.", throwable);
      return;
    }

    this.logger().info(
        "Voice Guard {} enabled. Mute everyone by default is {}.",
        this.addonInfo().getVersion(),
        this.configuration().muteByDefault().get() ? "on" : "off");
  }

  @Override
  protected Class<VoiceGuardConfiguration> configurationClass() {
    return VoiceGuardConfiguration.class;
  }

  /**
   * Undoes the addon's mutes the moment either switch is turned off, rather than waiting for the
   * next sweep.
   */
  private void registerReleaseOnDisable() {
    VoiceGuardConfiguration configuration = this.configuration();

    configuration.enabled().addChangeListener(value -> {
      if (Boolean.FALSE.equals(value)) {
        this.autoMuteService.releaseEverything();
      }
    });

    configuration.muteByDefault().addChangeListener(value -> {
      if (Boolean.FALSE.equals(value)) {
        this.autoMuteService.releaseMutes();
      }
    });
  }

  /** Leaves the client as it was found when the game closes. */
  @Subscribe
  public void onShutdown(ShutdownEvent event) {
    if (this.autoMuteService != null) {
      this.autoMuteService.releaseEverything();
    }
  }

  @Override
  public void logError(String message, Throwable throwable) {
    this.logger().error(message, throwable);
  }

  @Override
  public void displayChatMessage(Component message) {
    this.displayMessage(message);
  }
}
