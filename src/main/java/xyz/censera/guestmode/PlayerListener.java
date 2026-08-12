package xyz.censera.guestmode;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PlayerListener implements Listener {

    private final GuestMode plugin;

    PlayerListener(GuestMode plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PluginConfig config = plugin.getPluginConfig();

        if (player.hasPermission("guestmode.bypass") || player.isWhitelisted()) {
            return;
        }

        if (config.isKickIfWhitelistEnabled()) {
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&', config.getKickMessage()));
            return;
        }

        plugin.getRegistry().add(player.getUniqueId());
        player.setGameMode(config.getGuestGameMode());
        player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&', config.getGuestJoinMessage().replace("%player%", player.getName())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getRegistry().remove(event.getPlayer().getUniqueId());
    }
}
