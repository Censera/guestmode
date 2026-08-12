package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class GuestModeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "kick-guests");

    private final GuestMode plugin;

    GuestModeCommand(GuestMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guestmode.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "kick-guests" -> handleKickGuests(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(ChatColor.GREEN + "GuestMode config reloaded.");
        plugin.getLogger().info(sender.getName() + " reloaded GuestMode config.");
    }

    private void handleList(CommandSender sender) {
        Set<UUID> guests = plugin.getRegistry().snapshot();

        if (guests.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No guests are currently online.");
            return;
        }

        List<String> names = new ArrayList<>(guests.size());
        for (UUID uuid : guests) {
            Player player = Bukkit.getPlayer(uuid);
            names.add(player != null ? player.getName() : "(offline:" + uuid + ")");
        }

        sender.sendMessage(ChatColor.GOLD + "Online guests (" + guests.size() + "): "
                + ChatColor.WHITE + String.join(", ", names));
    }

    private void handleKickGuests(CommandSender sender) {
        Set<UUID> guests = plugin.getRegistry().snapshot();

        if (guests.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No guests to kick.");
            return;
        }

        int kicked = 0;
        for (UUID uuid : guests) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.kickPlayer(ChatColor.RED + "You have been removed from the server.");
                kicked++;
            }
            plugin.getRegistry().remove(uuid);
        }

        sender.sendMessage(ChatColor.GREEN + "Kicked " + kicked + " guest(s).");
        plugin.getLogger().info(sender.getName() + " kicked " + kicked + " guest(s).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("guestmode.admin") || args.length != 1) {
            return List.of();
        }

        String partial = args[0].toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(partial)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "GuestMode commands:");
        sender.sendMessage(ChatColor.YELLOW + "  /guestmode reload");
        sender.sendMessage(ChatColor.YELLOW + "  /guestmode list");
        sender.sendMessage(ChatColor.YELLOW + "  /guestmode kick-guests");
    }
}
