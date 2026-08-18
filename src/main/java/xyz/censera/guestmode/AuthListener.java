package xyz.censera.guestmode;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.PremiumStatus;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

        FastLoginBukkit fastLogin = plugin.getFastLogin();
        if (fastLogin != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && fastLogin.getStatus(uuid) == PremiumStatus.PREMIUM) {
                    plugin.getAuthenticated().add(uuid);
                    player.sendMessage(ChatColor.GREEN + "Premium account authenticated.");
                } else if (player.isOnline()) {
                    requireLogin(player);
                }
            }, 10L);
        } else {
            requireLogin(player);
        }
    }

    private void requireLogin(Player player) {
        if (plugin.getAuth().isRegistered(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Please log in with /login <password> [2fa-code].");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Please register with /register <password>.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAuthenticated().remove(event.getPlayer().getUniqueId());
    }

    private boolean blocked(Player player) {
        return !plugin.getAuthenticated().contains(player.getUniqueId());
    }

    @EventHandler public void onBreak(BlockBreakEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onDamage(EntityDamageByEntityEvent event) { if (event.getDamager() instanceof Player p && blocked(p)) event.setCancelled(true); }
    @EventHandler public void onInventory(InventoryOpenEvent event) { if (event.getPlayer() instanceof Player p && blocked(p)) event.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }
}
