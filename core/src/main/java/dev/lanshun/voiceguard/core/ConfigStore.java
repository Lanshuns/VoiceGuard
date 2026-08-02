package dev.lanshun.voiceguard.core;

/** Shared access to the addon configuration with batched saving. */
final class ConfigStore {
  private final VoiceGuardHost host;
  private boolean dirty;

  ConfigStore(VoiceGuardHost host) {
    this.host = host;
  }

  VoiceGuardConfiguration config() {
    return this.host.configuration();
  }

  void markDirty() {
    this.dirty = true;
  }

  void flush() {
    if (!this.dirty) {
      return;
    }

    this.dirty = false;

    try {
      this.host.saveConfiguration();
    } catch (Throwable throwable) {
      this.host.logError("Could not save the configuration.", throwable);
    }
  }
}
