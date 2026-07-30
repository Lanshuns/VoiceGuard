package dev.lanshun.voiceguard.core;

import net.labymod.api.client.component.Component;

/**
 * The part of the addon that {@link AutoMuteService} depends on, kept as an interface because
 * {@code LabyAddon} can only be instantiated inside a running client.
 */
public interface VoiceGuardHost {

  /** The addon configuration. */
  VoiceGuardConfiguration configuration();

  /** Persists the configuration. */
  void saveConfiguration();

  /** Reports a recoverable failure to the client log. */
  void logError(String message, Throwable throwable);

  /** Sends a client side chat message to the user. */
  void displayChatMessage(Component message);
}
