package dev.lanshun.voiceguard.core;

import net.labymod.api.client.component.Component;

/** What the service needs from the addon, as an interface because LabyAddon needs a running client. */
public interface VoiceGuardHost {
  VoiceGuardConfiguration configuration();

  void saveConfiguration();

  void logError(String message, Throwable throwable);

  void displayChatMessage(Component message);
}
