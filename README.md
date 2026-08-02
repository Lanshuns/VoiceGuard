<img src="store-assets/icon.png" alt="Voice Guard" width="128">

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

Muting and unmuting stays in VoiceChat's own controls: middle click a player and choose
**VoiceChat**, or run `/vm <name>`. Voice Guard notices either one and remembers an unmute, so that
player stays audible on any server.

It also stays out of your way:

* Only players with no volume entry at all are touched, so any volume you chose is never overridden.
* Every mute it applies is recorded and persisted, so its own entries can always be told apart from
  yours and cleaned up, even after a crash.
* Turning the addon off removes only its mutes and leaves yours alone.

## Commands

`/vg` lists the players in voice range, split into who you can hear and who was auto muted. Hover a
name to see why it is in its group. `/voiceguard` is the long form. The command only reports; it
never changes anything.

Shortly after joining a server the addon prints a summary of how many players you can hear and how
many were auto muted.

## Settings

The toggle beside the addon name is the master switch. Turning it off releases every mute the addon
applied and hands your microphone back; your own manual mutes are kept.

| Setting | Default | Meaning |
|---|---|---|
| Mute everyone by default | on | Players you have not unmuted start muted. Turn this off to run the addon for the microphone feature alone. |
| Always hear friends | on | Players on your LabyMod friends list are never muted automatically. |
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

The installable addon is written to `build/libs/voiceguard-release.jar`. Copy it into your LabyMod
addons folder and restart the game:

* **Windows**: `%APPDATA%\.minecraft\labymod-neo\addons\`
* **macOS**: `~/Library/Application Support/minecraft/labymod-neo/addons/`
* **Linux**: `~/.minecraft/labymod-neo/addons/`

`store-assets/icon.png` is the 512x512 master, which is also the image the addon store uses. The
256x256 copy shipped inside the jar lives at
`core/src/main/resources/assets/voiceguard/textures/icon.png`.

The VoiceChat API is not published to Maven and does not need to be vendored. The `addon("voicechat")`
line in `build.gradle.kts` makes labygradle download the published addon and place it on the compile
classpath, so this repository contains no third party code.
