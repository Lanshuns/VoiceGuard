import dev.lanshun.voiceguard.core.AutoMuteService;
import dev.lanshun.voiceguard.core.VoiceChatBridge;
import dev.lanshun.voiceguard.core.VoiceGuardConfiguration;
import dev.lanshun.voiceguard.core.VoiceGuardHost;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exercises the real AutoMuteService against a stubbed VoiceChat volume map.
 *
 * <p>Runs outside Minecraft so the mute decisions can be checked on any machine, via verify/run.sh
 * after a Gradle build.
 *
 * <p>This file uses {@code System.out}, which the addon store guidelines disallow for addon code.
 * It is a standalone console program that is never compiled into the addon jar and never runs
 * inside the client, so printing to standard output is the only way it can report anything. No
 * class in core/ prints to standard output.
 */
public final class AutoMuteVerification {

  private static int failures;

  public static void main(String[] args) {
    freshPlayerIsMuted();
    clientIsNeverMuted();
    userVolumeIsNeverOverridden();
    existingUserMuteIsNotClaimed();
    manualUnmuteIsDetectedAndRemembered();
    manualUnmuteIsNotRememberedWhenDisabled();
    allowlistedPlayerIsSkipped();
    disablingTheSettingReleasesOnlyOurMutes();
    disablingTheAddonReleasesEverything();
    microphoneGuardWorksWithoutMuteByDefault();
    handMutesAreToldApartAndRemembered();
    offlineHandMutesResolveByName();
    autoMutedAndHandMutedAreTrackedSeparately();
    staleMutesFromAPreviousSessionAreCleared();
    staleCleanupSparesUserChosenVolumes();
    friendingSomebodyReleasesOurMute();
    friendingDoesNotUndoAMuteYouChose();
    friendingDoesNothingWhenExemptionIsOff();
    menuUnmuteMakesThemAudibleAndRemembered();
    menuMuteOutlivesTheAddonBeingDisabled();
    isMutedReportsWhatTheMenuShows();
    micGoesQuietWhenABlockedPlayerCanHearYou();
    micComesBackWhenTheyLeave();
    micGuardNeverClaimsAMuteYouSetYourself();
    micGuardStandsDownIfYouUnmuteAnyway();
    micGuardReArmsAfterTheyLeaveAndReturn();
    guardCountsEveryoneYouCannotHear();
    listingIsPerServerWhileTheAllowlistIsGlobal();

    if (failures > 0) {
      System.out.println("\n" + failures + " check(s) FAILED");
      System.exit(1);
    }
    System.out.println("\nAll checks passed.");
  }

  // --- the scenarios -------------------------------------------------------------------------

  private static void freshPlayerIsMuted() {
    Fixture f = new Fixture();
    UUID stranger = UUID.randomUUID();

    f.service.guard(stranger, false);

    check("a player nobody has an opinion about is muted on sight",
        f.volumes.get(stranger) != null && f.volumes.get(stranger) == 0.0F);
  }

  private static void clientIsNeverMuted() {
    Fixture f = new Fixture();
    UUID self = UUID.randomUUID();

    f.service.guard(self, true);

    check("you are never muted for yourself", !f.volumes.containsKey(self));
  }

  private static void userVolumeIsNeverOverridden() {
    Fixture f = new Fixture();
    UUID friend = UUID.randomUUID();
    f.volumes.put(friend, 0.8F);

    f.service.guard(friend, false);

    check("a volume the user chose is left untouched", f.volumes.get(friend) == 0.8F);
  }

  private static void existingUserMuteIsNotClaimed() {
    Fixture f = new Fixture();
    UUID blocked = UUID.randomUUID();
    f.volumes.put(blocked, 0.0F);

    f.service.guard(blocked, false);
    f.service.releaseAll();

    check("a mute the user set themselves survives releaseAll",
        f.volumes.containsKey(blocked) && f.volumes.get(blocked) == 0.0F);
  }

  private static void manualUnmuteIsDetectedAndRemembered() {
    Fixture f = new Fixture();
    UUID player = UUID.randomUUID();

    f.service.guard(player, false);
    check("auto-muted before unmute", f.volumes.get(player) == 0.0F);

    // The user drags the slider up in VoiceChat's own user panel.
    f.volumes.put(player, 1.0F);
    f.service.sweep();

    check("manual unmute lands in the persisted allowlist",
        f.config.allowlist().get().containsKey(player));

    // Whatever hook sees them next must leave them alone.
    f.service.guard(player, false);
    f.service.sweep();
    check("an unmuted player is never re-muted", f.volumes.get(player) == 1.0F);
  }

  private static void manualUnmuteIsNotRememberedWhenDisabled() {
    Fixture f = new Fixture();
    f.config.rememberUnmuted().set(false);
    UUID player = UUID.randomUUID();

    f.service.guard(player, false);
    f.volumes.put(player, 1.0F);
    f.service.sweep();
    f.service.guard(player, false);

    check("with 'remember' off the allowlist stays empty",
        f.config.allowlist().get().isEmpty());
    check("but they stay audible for the rest of the session",
        f.volumes.get(player) == 1.0F);
  }

  private static void allowlistedPlayerIsSkipped() {
    Fixture f = new Fixture();
    UUID trusted = UUID.randomUUID();
    f.config.allowlist().get().put(trusted, "Trusted");

    f.service.guard(trusted, false);

    check("an allowlisted player is never muted", !f.volumes.containsKey(trusted));
  }

  private static void disablingTheSettingReleasesOnlyOurMutes() {
    Fixture f = new Fixture();
    UUID ours = UUID.randomUUID();
    UUID theirs = UUID.randomUUID();
    f.volumes.put(theirs, 0.0F); // muted by the user before we ever ran

    f.service.guard(ours, false);
    f.service.guard(theirs, false);

    f.config.muteByDefault().set(false);
    f.service.sweep();

    check("our mute is dropped when mute by default is turned off", !f.volumes.containsKey(ours));
    check("the user's own mute is kept", f.volumes.containsKey(theirs));
  }

  /** Disabling the addon must undo everything, since a disabled addon stops receiving events. */
  private static void disablingTheAddonReleasesEverything() {
    Fixture f = new Fixture();
    UUID ours = UUID.randomUUID();
    UUID theirs = UUID.randomUUID();
    f.volumes.put(theirs, 0.0F);

    f.service.guard(ours, false);
    f.service.guard(theirs, false);
    check("muted while enabled", f.volumes.get(ours) == 0.0F);

    f.service.releaseEverything();

    check("disabling releases our mute", !f.volumes.containsKey(ours));
    check("but still keeps a mute the user set", f.volumes.containsKey(theirs));
  }

  /** The microphone guard must work without mute by default, which is the point of separating them. */
  private static void microphoneGuardWorksWithoutMuteByDefault() {
    Fixture f = new Fixture();
    UUID blocked = UUID.randomUUID();

    f.config.muteByDefault().set(false);
    f.config.microphoneGuard().set(true);

    UUID stranger = UUID.randomUUID();
    f.service.guard(stranger, false);
    check("nobody is auto muted when mute by default is off", !f.volumes.containsKey(stranger));

    f.service.mute(blocked);
    check("but a hand muted player still counts for the guard", f.service.isMuted(blocked));
    check("and the guard still engages",
        f.service.decideMicrophone(true, true, false) == AutoMuteService.MicrophoneAction.MUTE);
  }

  private static void staleMutesFromAPreviousSessionAreCleared() {
    Fixture f = new Fixture();
    UUID leftover = UUID.randomUUID();

    // Simulate a crashed session: the mute is in VoiceChat's config and recorded as ours.
    f.volumes.put(leftover, 0.0F);
    f.config.managedMutes().get().add(leftover);

    f.service.sweep();

    check("a mute left behind by a crashed session is cleaned up on the first sweep",
        !f.volumes.containsKey(leftover));
    check("and the record of it is cleared", f.config.managedMutes().get().isEmpty());
  }

  private static void staleCleanupSparesUserChosenVolumes() {
    Fixture f = new Fixture();
    UUID recovered = UUID.randomUUID();

    // Recorded as ours, but the user has since raised the volume.
    f.volumes.put(recovered, 0.6F);
    f.config.managedMutes().get().add(recovered);

    f.service.sweep();

    check("startup cleanup never removes an audible volume", f.volumes.get(recovered) == 0.6F);
  }

  private static void friendingSomebodyReleasesOurMute() {
    Fixture f = new Fixture();
    UUID player = UUID.randomUUID();

    f.service.guard(player, false);
    check("muted before friending", f.service.isMuted(player));

    f.service.onFriendAdded(player);

    check("befriending releases the mute immediately", !f.service.isMuted(player));
    check("and they are not re-muted by the next sweep",
        sweepThenMuted(f, player) == false);
  }

  private static void friendingDoesNotUndoAMuteYouChose() {
    Fixture f = new Fixture();
    UUID player = UUID.randomUUID();

    f.service.mute(player); // the user muted them deliberately
    f.service.onFriendAdded(player);

    check("befriending does not unblock somebody you muted on purpose",
        f.service.isMuted(player));
  }

  private static void friendingDoesNothingWhenExemptionIsOff() {
    Fixture f = new Fixture();
    f.config.exemptFriends().set(false);
    UUID player = UUID.randomUUID();

    f.service.guard(player, false);
    f.service.onFriendAdded(player);

    check("with 'always hear friends' off, friending changes nothing",
        f.service.isMuted(player));
  }

  private static void menuUnmuteMakesThemAudibleAndRemembered() {
    Fixture f = new Fixture();
    UUID player = UUID.randomUUID();

    f.service.guard(player, false);
    f.service.unmute(player); // the new middle-click menu entry

    check("menu unmute makes them audible", !f.service.isMuted(player));
    check("menu unmute is remembered", f.config.allowlist().get().containsKey(player));
    check("and they stay audible across sweeps", sweepThenMuted(f, player) == false);
  }

  private static void menuMuteOutlivesTheAddonBeingDisabled() {
    Fixture f = new Fixture();
    UUID player = UUID.randomUUID();

    f.service.unmute(player);
    f.service.mute(player); // user changes their mind via the menu

    check("menu mute takes effect", f.service.isMuted(player));
    check("menu mute clears them from the allowlist",
        !f.config.allowlist().get().containsKey(player));

    f.config.enabled().set(false);
    f.service.sweep();

    check("a mute you chose survives VoiceGuard being switched off",
        f.service.isMuted(player));
  }

  private static void isMutedReportsWhatTheMenuShows() {
    Fixture f = new Fixture();
    UUID player = UUID.randomUUID();

    check("an unknown player reads as audible", !f.service.isMuted(player));
    f.service.guard(player, false);
    check("an auto-muted player reads as muted", f.service.isMuted(player));
    f.service.unmute(player);
    check("an unmuted player reads as audible", !f.service.isMuted(player));
  }

  // --- the microphone guard ------------------------------------------------------------------
  // Signature: decideMicrophone(guardEnabled, blockedPlayerInEarshot, micCurrentlyMuted)

  private static void micGoesQuietWhenABlockedPlayerCanHearYou() {
    Fixture f = new Fixture();

    check("mic is cut when a blocked player comes in earshot",
        f.service.decideMicrophone(true, true, false) == AutoMuteService.MicrophoneAction.MUTE);
    check("and it is not re-applied every tick",
        f.service.decideMicrophone(true, true, true) == AutoMuteService.MicrophoneAction.NONE);
  }

  private static void micComesBackWhenTheyLeave() {
    Fixture f = new Fixture();

    f.service.decideMicrophone(true, true, false); // guard engages
    check("mic is restored once they are out of earshot",
        f.service.decideMicrophone(true, false, true) == AutoMuteService.MicrophoneAction.UNMUTE);
    check("and it is not restored twice",
        f.service.decideMicrophone(true, false, false) == AutoMuteService.MicrophoneAction.NONE);
  }

  private static void micGuardNeverClaimsAMuteYouSetYourself() {
    Fixture f = new Fixture();

    // Your mic was already muted before a blocked player turned up.
    check("an existing mic mute is never claimed",
        f.service.decideMicrophone(true, true, true) == AutoMuteService.MicrophoneAction.NONE);
    // So when they leave, we must not un-mute it for you.
    check("and it is left alone when they leave",
        f.service.decideMicrophone(true, false, true) == AutoMuteService.MicrophoneAction.NONE);
  }

  private static void micGuardStandsDownIfYouUnmuteAnyway() {
    Fixture f = new Fixture();

    f.service.decideMicrophone(true, true, false); // guard engages
    // You un-mute yourself deliberately while they are still nearby.
    check("the guard stands down when you override it",
        f.service.decideMicrophone(true, true, false) == AutoMuteService.MicrophoneAction.NONE);
    check("and it does not fight you on the next tick",
        f.service.decideMicrophone(true, true, false) == AutoMuteService.MicrophoneAction.NONE);
  }

  private static void micGuardReArmsAfterTheyLeaveAndReturn() {
    Fixture f = new Fixture();

    f.service.decideMicrophone(true, true, false);  // engages
    f.service.decideMicrophone(true, true, false);  // you override it
    f.service.decideMicrophone(true, false, false); // they leave

    check("the guard arms again the next time they show up",
        f.service.decideMicrophone(true, true, false) == AutoMuteService.MicrophoneAction.MUTE);
  }

  /**
   * The distinction that keeps the guard usable. In a busy public channel almost everybody is
   * auto-muted; if those counted as blocked, the microphone would never come back on.
   */
  /** The guard now counts anybody you cannot hear, however they came to be muted. */
  private static void guardCountsEveryoneYouCannotHear() {
    Fixture f = new Fixture();
    UUID stranger = UUID.randomUUID();
    UUID blocked = UUID.randomUUID();
    UUID audible = UUID.randomUUID();

    f.service.guard(stranger, false);   // auto-muted by default
    f.service.mute(blocked);            // muted by hand
    f.service.unmute(audible);          // explicitly audible

    check("the guard counts an auto-muted stranger", f.service.isMuted(stranger));
    check("the guard counts somebody muted by hand", f.service.isMuted(blocked));
    check("the guard ignores somebody you can hear", !f.service.isMuted(audible));
    check("the guard ignores an unknown player", !f.service.isMuted(UUID.randomUUID()));
  }

  /**
   * The allowlist is a global decision about a person, but the listing must stay actionable, so
   * somebody unmuted on another server belongs in /vg all and not in /vg.
   */
  private static void listingIsPerServerWhileTheAllowlistIsGlobal() {
    Fixture f = new Fixture();
    UUID elsewhere = UUID.randomUUID();
    f.config.allowlist().get().put(elsewhere, "Elsewhere");

    check("a player unmuted on another server is not in the per server listing",
        !contains(f.service.audiblePlayers(), "Elsewhere"));
    check("but is still in the global allowlist listing",
        contains(f.service.allowlistedPlayers(), "Elsewhere"));
    check("and is still never re-muted",
        muteThenCheck(f, elsewhere));
  }

  /** Hand mutes are listed by name and dropped on unmute. */
  private static void handMutesAreToldApartAndRemembered() {
    Fixture f = new Fixture();
    UUID byHand = UUID.randomUUID();

    f.service.mute(byHand);

    f.config.manualMuteNames().get().put(byHand, "Target");
    check("the muted by you list carries the remembered name",
        contains(f.service.manuallyMutedPlayers(), "Target"));

    f.service.unmute(byHand);
    check("unmuting removes them from the list",
        !contains(f.service.manuallyMutedPlayers(), "Target"));

    UUID stale = UUID.randomUUID();
    f.config.manualMuteNames().get().put(stale, "Gone");
    f.service.sweep();
    check("a name whose mute no longer exists is pruned on sweep",
        !contains(f.service.manuallyMutedPlayers(), "Gone"));
  }

  /** A yellow offline entry must stay clickable, so its stored name has to resolve to its id. */
  private static void offlineHandMutesResolveByName() {
    Fixture f = new Fixture();
    UUID gone = UUID.randomUUID();
    f.volumes.put(gone, 0.0F);
    f.config.manualMuteNames().get().put(gone, "GoneFriend");

    check("a name known only from the manual mute record still resolves",
        gone.equals(f.service.resolveByName("GoneFriend")));
    check("and unresolvable names still return nothing",
        f.service.resolveByName("NeverSeen") == null);
  }

  /** The session group's Auto muted line must never claim a mute the user made by hand. */
  private static void autoMutedAndHandMutedAreTrackedSeparately() {
    Fixture f = new Fixture();
    UUID stranger = UUID.randomUUID();
    UUID byHand = UUID.randomUUID();

    f.service.guard(stranger, false);
    f.service.mute(byHand);

    check("an addon mute is recorded as managed",
        f.config.managedMutes().get().contains(stranger));
    check("a hand mute is not recorded as managed",
        !f.config.managedMutes().get().contains(byHand));
  }

  private static boolean contains(java.util.List<AutoMuteService.PlayerEntry> entries, String name) {
    for (AutoMuteService.PlayerEntry entry : entries) {
      if (entry.name().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static boolean muteThenCheck(Fixture f, UUID player) {
    f.service.guard(player, false);
    return !f.service.isMuted(player);
  }

  private static boolean sweepThenMuted(Fixture f, UUID player) {
    f.service.guard(player, false);
    f.service.sweep();
    return f.service.isMuted(player);
  }

  // --- plumbing ------------------------------------------------------------------------------

  private static void check(String what, boolean ok) {
    System.out.println((ok ? "  ok   " : "  FAIL ") + what);
    if (!ok) {
      failures++;
    }
  }

  /** A service wired to an in-memory volume map instead of a running VoiceChat addon. */
  private static final class Fixture {

    final VoiceGuardConfiguration config = new VoiceGuardConfiguration();
    final Map<UUID, Float> volumes = new HashMap<>();
    final AutoMuteService service;

    Fixture() {
      VoiceChatBridge bridge = new VoiceChatBridge() {
        @Override
        public boolean isAvailable() {
          return true;
        }

        @Override
        public Map<UUID, Float> playerVolumes() {
          return Fixture.this.volumes;
        }
      };

      VoiceGuardHost host = new VoiceGuardHost() {
        @Override
        public VoiceGuardConfiguration configuration() {
          return Fixture.this.config;
        }

        @Override
        public void saveConfiguration() {
          // no-op: persistence is LabyMod's job
        }

        @Override
        public void displayChatMessage(net.labymod.api.client.component.Component message) {
          // no-op: chat only exists inside a running client
        }

        @Override
        public void logError(String message, Throwable throwable) {
          System.out.println("  (log) " + message);
        }
      };

      this.service = new AutoMuteService(host, bridge);
    }
  }
}
