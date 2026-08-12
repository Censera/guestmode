package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

final class UpgradeTask {

    private final GuestMode plugin;
    private BukkitTask task;

    UpgradeTask(GuestMode plugin) {
        this.plugin = plugin;
    }

    void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        GuestRegistry registry = plugin.getRegistry();
        PluginConfig config = plugin.getPluginConfig();

        for (UUID uuid : registry.snapshot()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                registry.remove(uuid);
                continue;
            }

            if (player.isWhitelisted()) {
                upgrade(player, config);
            }
        }
    }

    private void upgrade(Player player, PluginConfig config) {
        plugin.getRegistry().remove(player.getUniqueId());
        player.setGameMode(config.getUpgradeGameMode());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getUpgradeMessage()));

        String broadcast = config.getBroadcastOnUpgrade();
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes(
                    '&', broadcast.replace("%player%", player.getName())));
        }

        plugin.getLogger().info(player.getName()
                + " whitelisted while online; upgraded from Guest Mode.");
    }
}
