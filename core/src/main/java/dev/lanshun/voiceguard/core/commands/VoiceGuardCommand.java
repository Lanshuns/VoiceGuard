package dev.lanshun.voiceguard.core.commands;

import dev.lanshun.voiceguard.core.AutoMuteService;
import dev.lanshun.voiceguard.core.AutoMuteService.PlayerEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.labymod.api.client.chat.command.Command;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.event.ClickEvent;
import net.labymod.api.client.component.event.HoverEvent;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;

/**
 * Lists who the user can hear and who is muted, with every name clickable to change it, scoped to
 * voice range by {@code /vg} and to every server by {@code /vg all}.
 */
public class VoiceGuardCommand extends Command {

  private final AutoMuteService autoMuteService;

  public VoiceGuardCommand(AutoMuteService autoMuteService) {
    super("voiceguard", "vg");
    this.autoMuteService = autoMuteService;
  }

  /** Handles {@code /vg}, {@code /vg all}, {@code /vg mute } and {@code /vg unmute }. */
  @Override
  public boolean execute(String prefix, String[] arguments) {
    if (arguments.length >= 2) {
      String action = arguments[0].toLowerCase();
      if (action.equals("mute") || action.equals("unmute")) {
        this.toggle(arguments[1], action.equals("mute"));
        return true;
      }
    }

    if (arguments.length >= 1 && arguments[0].equalsIgnoreCase("all")) {
      this.showAllTime();
      return true;
    }

    this.showNearby();
    return true;
  }

  /** Mutes or unmutes the named player and confirms it in one line. */
  private void toggle(String name, boolean mute) {
    UUID uniqueId = this.autoMuteService.resolveByName(name);
    if (uniqueId == null) {
      this.displayMessage(
          Component.translatable("voiceguard.command.notFound", NamedTextColor.RED)
              .append(Component.text(" " + name, NamedTextColor.GRAY)));
      return;
    }

    if (mute) {
      this.autoMuteService.mute(uniqueId);
    } else {
      this.autoMuteService.unmute(uniqueId);
    }

    this.displayMessage(
        Component.translatable(
                mute ? "voiceguard.command.didMute" : "voiceguard.command.didUnmute",
                NamedTextColor.GRAY)
            .append(Component.text(" " + name, mute ? NamedTextColor.RED : NamedTextColor.GREEN)));
  }

  /** Prints the players currently in voice range, which is what the user can act on now. */
  private void showNearby() {
    this.displayMessage(Component.translatable("voiceguard.command.header", NamedTextColor.AQUA));
    this.displayMessage(this.groupHeader("voiceguard.command.nearbyHeader"));

    this.displayMessage(this.section(
        "voiceguard.command.audible", this.autoMuteService.audiblePlayers(), true));
    this.displayMessage(this.section(
        "voiceguard.command.autoMuted", this.autoMuteService.autoMutedPlayers(), false));

    this.displayMessage(Component.translatable("voiceguard.command.hint", NamedTextColor.GRAY));
    this.displayMessage(commandHint(
        "voiceguard.command.allHintBefore", "/vg all", "voiceguard.command.allHintAfter"));
  }

  /** Prints the user's standing decisions across every server. */
  private void showAllTime() {
    this.displayMessage(Component.translatable("voiceguard.command.header", NamedTextColor.AQUA));
    this.displayMessage(this.groupHeader("voiceguard.command.allTimeHeader"));

    this.displayMessage(this.section(
        "voiceguard.command.unmutedByYou", this.unmutedElsewhere(), true));
    this.displayMessage(this.section(
        "voiceguard.command.mutedByYou", this.autoMuteService.manuallyMutedPlayers(), false));

    this.displayMessage(Component.translatable("voiceguard.command.hint", NamedTextColor.GRAY));
    this.displayMessage(commandHint(
        "voiceguard.command.nearbyHintBefore", "/vg", "voiceguard.command.nearbyHintAfter"));
  }

  /** Allowlisted players who are not already audible here, so nobody is listed twice. */
  private List<PlayerEntry> unmutedElsewhere() {
    Set<UUID> present = new HashSet<>();
    for (PlayerEntry entry : this.autoMuteService.audiblePlayers()) {
      present.add(entry.uniqueId());
    }

    return withoutPresent(this.autoMuteService.allowlistedPlayers(), present);
  }

  /** A hint line with the command quoted and picked out in white. */
  private static Component commandHint(String beforeKey, String command, String afterKey) {
    return Component.translatable(beforeKey, NamedTextColor.GRAY)
        .append(Component.text(" '" + command + "' ", NamedTextColor.WHITE))
        .append(Component.translatable(afterKey, NamedTextColor.GRAY));
  }

  /** A separator line naming the group that follows. */
  private Component groupHeader(String key) {
    return Component.text("\u2500\u2500 ", NamedTextColor.GRAY)
        .append(Component.translatable(key, NamedTextColor.AQUA))
        .append(Component.text(" \u2500\u2500", NamedTextColor.GRAY));
  }

  /** Filters out players already listed in the present sections. */
  private static List<PlayerEntry> withoutPresent(List<PlayerEntry> entries, Set<UUID> present) {
    List<PlayerEntry> filtered = new ArrayList<>();
    for (PlayerEntry entry : entries) {
      if (!present.contains(entry.uniqueId())) {
        filtered.add(entry);
      }
    }

    return filtered;
  }

  /** Builds one line of the listing, each name running the opposite action when clicked. */
  private Component section(String key, List<PlayerEntry> entries, boolean audible) {
    Component line = Component.translatable(key, NamedTextColor.WHITE)
        .append(Component.text(" (" + entries.size() + ") ", NamedTextColor.GRAY));

    if (entries.isEmpty()) {
      return line.append(
          Component.translatable("voiceguard.command.none", NamedTextColor.GRAY));
    }

    String action = audible ? "mute" : "unmute";
    String hoverKey = audible
        ? "voiceguard.command.clickToMute"
        : "voiceguard.command.clickToUnmute";
    TextColor colour = audible ? NamedTextColor.GREEN : NamedTextColor.RED;

    boolean first = true;
    for (PlayerEntry entry : entries) {
      if (!first) {
        line = line.append(Component.text(", ", NamedTextColor.GRAY));
      }
      first = false;

      line = line.append(
          Component.text("[" + entry.name() + "]").color(colour)
              .clickEvent(ClickEvent.runCommand("/voiceguard " + action + " " + entry.name()))
              .hoverEvent(HoverEvent.showText(
                  Component.translatable(hoverKey, NamedTextColor.YELLOW))));
    }

    return line;
  }
}
