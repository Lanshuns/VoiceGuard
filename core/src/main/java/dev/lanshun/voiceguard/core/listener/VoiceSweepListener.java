package dev.lanshun.voiceguard.core.listener;

import dev.lanshun.voiceguard.core.AutoMuteService;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;

/** Drives the periodic sweep, which is the backstop for anything the events miss. */
public class VoiceSweepListener {

  private static final int SWEEP_INTERVAL_TICKS = 10;

  private final AutoMuteService autoMuteService;
  private int counter;

  public VoiceSweepListener(AutoMuteService autoMuteService) {
    this.autoMuteService = autoMuteService;
  }

  /** Runs the sweep once the interval has elapsed. */
  @Subscribe
  public void onGameTick(GameTickEvent event) {
    if (event.phase() != Phase.PRE) {
      return;
    }

    if (++this.counter < SWEEP_INTERVAL_TICKS) {
      return;
    }

    this.counter = 0;

    try {
      this.autoMuteService.sweep();
    } catch (Throwable throwable) {
      this.autoMuteService.logSweepFailure(throwable);
    }
  }
}
