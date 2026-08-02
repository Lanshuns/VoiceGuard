package dev.lanshun.voiceguard.core.commands;

import dev.lanshun.voiceguard.core.AutoMuteService;
import dev.lanshun.voiceguard.core.PlayerEntry;
import java.util.List;
import net.labymod.api.client.chat.command.Command;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.event.HoverEvent;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;

/** Shows who is in voice range and why you can or cannot hear them. */
public class VoiceGuardCommand extends Command {
  private final AutoMuteService autoMuteService;

  public VoiceGuardCommand(AutoMuteService autoMuteService) {
    super("voiceguard", "vg");
    this.autoMuteService = autoMuteService;
  }

  @Override
  public boolean execute(String prefix, String[] arguments) {
    this.displayMessage(Component.translatable("voiceguard.command.header", NamedTextColor.AQUA));
    this.displayMessage(this.groupHeader("voiceguard.command.nearbyHeader"));

    this.displayMessage(this.section(
        "voiceguard.command.audible", this.autoMuteService.audiblePlayers(), false));
    this.displayMessage(this.section(
        "voiceguard.command.autoMuted", this.autoMuteService.autoMutedPlayers(), true));

    this.displayMessage(
        Component.translatable("voiceguard.command.changeHintBefore", NamedTextColor.GRAY)
            .append(Component.text(" '/vm <name>' ", NamedTextColor.WHITE))
            .append(Component.translatable(
                "voiceguard.command.changeHintAfter", NamedTextColor.GRAY)));
    return true;
  }

  private Component groupHeader(String key) {
    return Component.text("\u2500\u2500 ", NamedTextColor.GRAY)
        .append(Component.translatable(key, NamedTextColor.AQUA))
        .append(Component.text(" \u2500\u2500", NamedTextColor.GRAY));
  }

  private Component section(String key, List<PlayerEntry> entries, boolean muted) {
    Component line = Component.translatable(key, NamedTextColor.WHITE)
        .append(Component.text(" (" + entries.size() + ") ", NamedTextColor.GRAY));

    if (entries.isEmpty()) {
      return line.append(Component.translatable("voiceguard.command.none", NamedTextColor.GRAY));
    }

    boolean first = true;
    for (PlayerEntry entry : entries) {
      if (!first) {
        line = line.append(Component.text(", ", NamedTextColor.GRAY));
      }
      first = false;

      line = line.append(this.name(entry, muted));
    }

    return line;
  }

  private Component name(PlayerEntry entry, boolean muted) {
    TextColor colour;
    String reasonKey;

    if (muted) {
      colour = NamedTextColor.RED;
      reasonKey = "voiceguard.command.reasonAutoMuted";
    } else if (this.autoMuteService.isFriend(entry.uniqueId())) {
      colour = NamedTextColor.YELLOW;
      reasonKey = "voiceguard.command.reasonFriend";
    } else {
      colour = NamedTextColor.GREEN;
      reasonKey = "voiceguard.command.reasonUnmuted";
    }

    return Component.text(entry.name()).color(colour)
        .hoverEvent(HoverEvent.showText(Component.translatable(reasonKey, NamedTextColor.GRAY)));
  }
}
