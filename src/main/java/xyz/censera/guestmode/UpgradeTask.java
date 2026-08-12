package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/*
 * Polls the guest registry every second on the main thread and upgrades any
 * player that has been whitelisted since they joined.
 *
 * Bukkit fires no event when a player is added to the whitelist, so polling
 * is the only option without NMS. One second latency is acceptable.
 */
public final class UpgradeTask {

    private final GuestMode plugin;
    private BukkitTask task;

    public UpgradeTask(GuestMode plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        GuestRegistry registry = plugin.getRegistry();
        PluginConfig cfg = plugin.getPluginConfig();

        /* Snapshot before iterating so that remove() inside the loop does not
         * race with the live set. The ConcurrentHashMap iterator is weakly
         * consistent, but an explicit snapshot makes the intent clear. */
        for (UUID uuid : registry.snapshot()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                registry.remove(uuid);
                continue;
            }

            if (player.isWhitelisted()) {
                upgrade(player, cfg);
            }
        }
    }

    private void upgrade(Player player, PluginConfig cfg) {
        plugin.getRegistry().remove(player.getUniqueId());
        player.setGameMode(cfg.getUpgradeGameMode());
        player.sendMessage(GuestMode.colorize(cfg.getUpgradeMessage()));

        String broadcast = cfg.getBroadcastOnUpgrade();
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(
                    GuestMode.colorize(broadcast.replace("%player%", player.getName())));
        }

        plugin.getLogger().info(player.getName()
                + " whitelisted while online; upgraded from Guest Mode.");
    }
}
