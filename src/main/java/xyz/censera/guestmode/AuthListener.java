package xyz.censera.guestmode;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

final class AuthListener implements Listener {
    private final GuestMode plugin;

    AuthListener(GuestMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.isFloodgatePlayer(uuid)) {
            plugin.getAuthenticated().add(uuid);
            return;
        }

        if (player.hasPermission("guestmode.bypass") || player.isWhitelisted()) {
            plugin.getAuthenticated().add(uuid);
            return;
        }

        plugin.enterGuest(player);
        player.sendMessage(ChatColor.YELLOW + "Please log in with /login <password> or register with /register <password>.");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.getAuthenticated().contains(uuid)) {
                return;
            }

            if (plugin.isPremiumPlayer(uuid)) {
                plugin.getAuthenticated().add(uuid);
                player.sendMessage(ChatColor.GREEN + "Premium account authenticated. You remain in Guest Mode until whitelisted.");
            } else if (plugin.getAuth().isRegistered(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "Please log in with /login <password> [2fa-code].");
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAuthenticated().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (blocked(event.getPlayer())) {
            String command = event.getMessage().toLowerCase();
            if (!command.startsWith("/login ") && !command.equals("/login")
                    && !command.startsWith("/register ") && !command.equals("/register")
                    && !command.startsWith("/guest ") && !command.equals("/guest")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "You must authenticate first.");
            }
        }
    }

    private boolean blocked(Player player) {
        return !plugin.getAuthenticated().contains(player.getUniqueId());
    }
}
