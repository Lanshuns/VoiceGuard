<img src="icon.png" alt="Voice Guard" width="128">

# Voice Guard

A LabyMod 4 addon that inverts the default of the official **VoiceChat** addon. Every voice chat
participant starts **muted**, and you unmute only the people you want to hear.

VoiceChat can only mute reactively: somebody joins, and you mute them after you have already heard
them. If the first thing they say is abuse, the damage is done. Voice Guard flips that around, so
nothing is audible until you say so.

## How it works

VoiceChat stores a per player volume map in its configuration, and a player is locally muted
exactly when their volume is `<= 0`:

```java
// net.labymod.addons.voicechat.api.configuration.VoiceChatConfiguration
float getVolumeOf(UUID uniqueId);                                       // default 1.0
default boolean isLocallyMuted(UUID uniqueId) { return getVolumeOf(uniqueId) <= 0; }
```

Voice Guard writes `0` into that same map through the public API, which is exactly what VoiceChat's
own mute button does. Nothing is patched, mixed in, or reflected into.

The important hook is `AudioStreamStartEvent`. VoiceChat creates the audio stream, fires the event,
and only then queues the first packet of audio, so muting there means **not a single frame of a
stranger's voice is ever played**.

### Unmuting

There are three ways, and they all do the same thing:

* Middle click a player and choose **Unmute**.
* Run `/vg` and click their name in the listing.
* Raise their volume slider in VoiceChat's own user panel.

Whichever you use, Voice Guard notices, hands that player back to you, and remembers them so they
stay audible the next time you meet, on any server. Unmuting is a decision about a person rather
than about a server, so it is deliberately not scoped to one. `/vg` shows only players present
where you are, and `/vg all` shows the full list for pruning.

### Staying out of the way

* Only players with no volume entry at all are touched, so any volume you chose is never overridden.
* Every mute it applies is recorded and persisted, so its own entries can always be told apart from
  yours and cleaned up, even after a crash.
* Turning the addon off removes only its mutes and leaves yours alone.

## Commands

| Command | Effect |
|---|---|
| `/vg` | Players currently in voice range: who you can hear and who was auto muted. Every name is clickable. |
| `/vg all` | Your standing decisions across every server: unmuted by you, muted by you. |
| `/vg mute <name>` | Mutes that player. |
| `/vg unmute <name>` | Unmutes that player. |

`/voiceguard` works as the long form of all three.

Shortly after joining a server the addon prints a short summary of how many players you can hear
and how many were auto muted.

In both listings, green always means you can hear them and red always means you cannot; the labels
say why. `/vg` deliberately shows only players in voice range, since those are the ones you can act
on, and points at `/vg all` for the rest.

## Settings

The toggle beside the addon name is the master switch. Turning it off releases every mute the addon
applied and hands your microphone back; your own manual mutes are kept.

| Setting | Default | Meaning |
|---|---|---|
| Mute everyone by default | on | Players you have not unmuted start muted. Turn this off to run the addon for the microphone feature alone. |
| Always hear friends | on | Players on your LabyMod friends list are never muted automatically. |
| Remember who I unmute | on | Unmutes persist across sessions. Off means they last until you quit. |
| Auto mute microphone | off | Mutes your own microphone while a muted player is close enough to hear you. |
| Distance that counts as earshot | 24 | How near a muted player must be, in blocks, before the guard engages. |

### A note on the microphone guard

Muting somebody stops you hearing them. It does not stop them hearing you, and no client side addon
can change that: your voice leaves the machine as a single unaddressed buffer that the voice server
copies to every listener, so there is no way to exclude one person from it.

The microphone guard is the only honest approximation. While a muted player is within the configured
radius it switches your microphone off entirely, which guarantees they cannot hear you at the cost
of nobody else hearing you either. It is off by default for that reason.

## Building

Requires **JDK 21**. On macOS: `brew install openjdk@21`.

```bash
./gradlew createReleaseJar
```

The installable addon is written to `build/libs/voiceguard-release.jar`.

`icon.png` in the repository root is the 512x512 master, which is also the image the addon store
uses. The 256x256 copy shipped inside the jar lives at
`core/src/main/resources/assets/voiceguard/textures/icon.png`.

The VoiceChat API is not published to Maven and does not need to be vendored. The `addon("voicechat")`
line in `build.gradle.kts` makes labygradle download the published addon and place it on the compile
classpath, so this repository contains no third party code.

## Installing for testing

Copy `build/libs/voiceguard-release.jar` into your LabyMod addons folder:

* **Windows**: `%APPDATA%\.minecraft\labymod-neo\addons\`
* **macOS**: `~/Library/Application Support/minecraft/labymod-neo/addons/`
* **Linux**: `~/.minecraft/labymod-neo/addons/`

Restart the game. Voice Guard appears in the LabyMod settings under its own category.

## Automated checks

The mute decisions run outside Minecraft, so they can be checked on any machine:

```bash
./gradlew :core:classes && verify/run.sh
```

This exercises the real `AutoMuteService` against a stubbed volume map and covers muting on sight,
never muting yourself, never overriding a volume you chose, detecting a manual unmute, the
allowlist, the friend exemption, releasing on disable, cleaning up mutes left by a crashed session,
and the microphone guard's state machine.

## Testing in game

The checks above cannot produce real audio, a second player, or VoiceChat's own configuration file,
so these four are verified by hand in game. Run them after changing anything in `AutoMuteService`.

1. **Zero leak**: join a public voice channel and have a second account talk the instant it joins.
   You should hear nothing, and VoiceChat's user panel should already show them muted.
2. **Selective unmute**: unmute them by any of the three routes. Audio returns immediately, and they
   are still audible after a rejoin.
3. **Friend exemption**: with *Always hear friends* on, adding somebody as a friend makes them
   audible without a restart.
4. **No configuration damage**: mute somebody yourself, disable the addon, then check
   `labymod-neo/configs/voicechat/settings.json`. Your own `playerVolumes` entry must survive.
