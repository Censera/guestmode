package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GuestModeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "kick-guests");

    private final GuestMode plugin;

    public GuestModeCommand(GuestMode plugin) {
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
            case "reload"      -> handleReload(sender);
            case "list"        -> handleList(sender);
            case "kick-guests" -> handleKickGuests(sender);
            default            -> sendUsage(sender);
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

        String names = guests.stream()
                .map(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return p != null ? p.getName() : "(offline:" + uuid + ")";
                })
                .collect(Collectors.joining(", "));

        sender.sendMessage(ChatColor.GOLD + "Online guests (" + guests.size() + "): "
                + ChatColor.WHITE + names);
    }

    private void handleKickGuests(CommandSender sender) {
        /* Snapshot before kicking, kick triggers PlayerQuitEvent which calls
         * registry.remove(), so the live set would shrink under us mid-loop. */
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
        if (!sender.hasPermission("guestmode.admin")) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "GuestMode commands:");
        sender.sendMessage(ChatColor.YELLOW + "  /guestmode reload");
        sender.sendMessage(ChatColor.YELLOW + "  /guestmode list");
        sender.sendMessage(ChatColor.YELLOW + "  /guestmode kick-guests");
    }
}
