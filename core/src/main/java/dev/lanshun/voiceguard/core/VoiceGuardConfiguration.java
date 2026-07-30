package dev.lanshun.voiceguard.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingRequires;

/** Persisted settings and state for the addon. */
@ConfigName("settings")
public class VoiceGuardConfiguration extends AddonConfig {

  /** The addon master switch. */
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  /** Whether unknown players start muted. */
  @SwitchSetting
  private final ConfigProperty<Boolean> muteByDefault = new ConfigProperty<>(true);

  /** Whether players on the user's LabyMod friends list are never muted automatically. */
  @SwitchSetting
  private final ConfigProperty<Boolean> exemptFriends = new ConfigProperty<>(true);

  /** Whether unmutes persist across sessions rather than lasting until the game closes. */
  @SwitchSetting
  private final ConfigProperty<Boolean> rememberUnmuted = new ConfigProperty<>(true);

  /** Switches the user's own microphone off while a muted player is close enough to hear them. */
  @SwitchSetting
  private final ConfigProperty<Boolean> microphoneGuard = new ConfigProperty<>(false);

  /** How close a muted player has to be, in blocks, before the guard engages. */
  @SliderSetting(min = 4, max = 128, steps = 1)
  @SettingRequires("microphoneGuard")
  private final ConfigProperty<Integer> guardRadius = new ConfigProperty<>(24);

  /** Players the user has chosen to hear, mapped to their last known name. */
  private final ConfigProperty<Map<UUID, String>> allowlist = new ConfigProperty<>(new HashMap<>());

  /** Names of players the user muted themselves, so they can be listed after logging off. */
  private final ConfigProperty<Map<UUID, String>> manualMuteNames =
      new ConfigProperty<>(new HashMap<>());

  /** Mutes this addon wrote into VoiceChat's configuration. */
  private final ConfigProperty<Set<UUID>> managedMutes = new ConfigProperty<>(new HashSet<>());

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Boolean> muteByDefault() {
    return this.muteByDefault;
  }

  public ConfigProperty<Boolean> exemptFriends() {
    return this.exemptFriends;
  }

  public ConfigProperty<Boolean> rememberUnmuted() {
    return this.rememberUnmuted;
  }

  public ConfigProperty<Boolean> microphoneGuard() {
    return this.microphoneGuard;
  }

  public ConfigProperty<Integer> guardRadius() {
    return this.guardRadius;
  }

  public ConfigProperty<Map<UUID, String>> allowlist() {
    return this.allowlist;
  }

  public ConfigProperty<Map<UUID, String>> manualMuteNames() {
    return this.manualMuteNames;
  }

  public ConfigProperty<Set<UUID>> managedMutes() {
    return this.managedMutes;
  }
}
