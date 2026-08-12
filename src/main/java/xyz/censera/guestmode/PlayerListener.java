package xyz.censera.guestmode;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private final GuestMode plugin;

    public PlayerListener(GuestMode plugin) {
        this.plugin = plugin;
    }

    /*
     * HIGH so we run after auth plugins that may kick at NORMAL (e.g. OpeNLogin),
     * but before MONITOR listeners that expect game state to be settled.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PluginConfig cfg = plugin.getPluginConfig();

        if (player.hasPermission("guestmode.bypass")) return;

        if (!player.isWhitelisted()) {
            /* kick-if-whitelist-enabled lets admins reject guests outright instead
             * of placing them in Adventure mode. The config flag alone controls
             * this; it is independent of whether Bukkit's whitelist enforcement
             * is active (whitelist=true in server.properties). */
            if (cfg.isKickIfWhitelistEnabled()) {
                player.kickPlayer(GuestMode.colorize(cfg.getKickMessage()));
                return;
            }

            plugin.getRegistry().add(player.getUniqueId());
            player.setGameMode(cfg.getGuestGameMode());
            player.sendMessage(GuestMode.colorize(
                    cfg.getGuestJoinMessage().replace("%player%", player.getName())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getRegistry().remove(event.getPlayer().getUniqueId());
    }
}
