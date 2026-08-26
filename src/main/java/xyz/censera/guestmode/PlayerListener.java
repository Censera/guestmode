package xyz.censera.guestmode;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class PlayerListener implements Listener {

    private final GuestMode plugin;

    PlayerListener(GuestMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PluginConfig config = plugin.getPluginConfig();

        if (player.hasPermission("eyes.bypass")) {
            return;
        }

        if (config.isKickIfWhitelistEnabled() && !player.isWhitelisted()) {
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&', config.getKickMessage()));
            return;
        }

        plugin.enterGuest(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        plugin.getRegistry().remove(event.getPlayer().getUniqueId());
    }
}
