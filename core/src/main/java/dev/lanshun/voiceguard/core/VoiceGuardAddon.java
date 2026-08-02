package dev.lanshun.voiceguard.core;

import dev.lanshun.voiceguard.core.commands.VoiceGuardCommand;
import dev.lanshun.voiceguard.core.listener.FriendListener;
import dev.lanshun.voiceguard.core.listener.ServerJoinListener;
import dev.lanshun.voiceguard.core.listener.VoiceStreamListener;
import dev.lanshun.voiceguard.core.listener.VoiceSweepListener;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.ShutdownEvent;
import net.labymod.api.models.addon.annotation.AddonMain;

/** Everyone in voice chat starts muted; the user unmutes who they want to hear. */
@AddonMain
public class VoiceGuardAddon extends LabyAddon<VoiceGuardConfiguration> implements VoiceGuardHost {
  private AutoMuteService autoMuteService;

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
