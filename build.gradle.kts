plugins {
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
}

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

group = "dev.lanshun.voiceguard"
version = providers.environmentVariable("VERSION").getOrElse("1.0.0")

labyMod {
    defaultPackageName = "dev.lanshun.voiceguard"

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    // Set to true to log in with a real Minecraft account when using runClient.
                    // devLogin = true
                }
            }
        }
    }

    addonInfo {
        namespace = "voiceguard"
        displayName = "Voice Guard"
        author = "Lanshun"
        description = "Everyone in voice chat is muted by default. Unmute the people you " +
                "want to hear. Run /vg to list who you can hear and who is muted, and click any " +
                "name to change it. Friends are never muted automatically. Optionally auto mutes " +
                "your microphone when a muted player is nearby."
        minecraftVersion = "*"
        version = rootProject.version.toString()

        // Required dependency on LabyMod's official VoiceChat addon. labygradle resolves the
        // published addon and puts it on the compile classpath, and the store installs it
        // alongside VoiceGuard.
        addon("voicechat")
    }
}

subprojects {
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")

    group = rootProject.group
    version = rootProject.version

    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
