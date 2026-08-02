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

@ConfigName("settings")
public class VoiceGuardConfiguration extends AddonConfig {
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> muteByDefault = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> exemptFriends = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> microphoneGuard = new ConfigProperty<>(false);

  @SliderSetting(min = 4, max = 128, steps = 1)
  @SettingRequires("microphoneGuard")
  private final ConfigProperty<Integer> guardRadius = new ConfigProperty<>(24);

  private final ConfigProperty<Map<UUID, String>> allowlist = new ConfigProperty<>(new HashMap<>());

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

  public ConfigProperty<Boolean> microphoneGuard() {
    return this.microphoneGuard;
  }

  public ConfigProperty<Integer> guardRadius() {
    return this.guardRadius;
  }

  public ConfigProperty<Map<UUID, String>> allowlist() {
    return this.allowlist;
  }

  public ConfigProperty<Set<UUID>> managedMutes() {
    return this.managedMutes;
  }
}
