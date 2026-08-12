package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class GuestMode extends JavaPlugin {

    private GuestRegistry registry;
    private PluginConfig pluginConfig;
    private UpgradeTask upgradeTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        registry = new GuestRegistry();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        upgradeTask = new UpgradeTask(this);
        upgradeTask.start();

        GuestModeCommand executor = new GuestModeCommand(this);
        var command = getCommand("guestmode");
        if (command == null) {
            throw new IllegalStateException("Required command 'guestmode' is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("Enabled. Unwhitelisted players join in "
                + pluginConfig.getGuestGameMode().name() + " mode.");
    }

    @Override
    public void onDisable() {
        if (upgradeTask != null) {
            upgradeTask.cancel();
            upgradeTask = null;
        }
        getLogger().info("Disabled.");
    }

    void reload() {
        reloadConfig();
        PluginConfig newConfig = new PluginConfig(this);
        pluginConfig = newConfig;

        for (UUID uuid : registry.snapshot()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                registry.remove(uuid);
                continue;
            }

            if (player.isWhitelisted()) {
                registry.remove(uuid);
                player.setGameMode(pluginConfig.getUpgradeGameMode());
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes(
                        '&', pluginConfig.getUpgradeMessage()));
            }
        }

        getLogger().info("Configuration reloaded.");
    }

    GuestRegistry getRegistry() {
        return registry;
    }

    PluginConfig getPluginConfig() {
        return pluginConfig;
    }
}
