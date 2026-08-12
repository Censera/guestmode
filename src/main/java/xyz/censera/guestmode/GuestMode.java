package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
        var cmd = getCommand("guestmode");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Failed to register /guestmode — check plugin.yml.");
        }

        getLogger().info("Enabled. Unwhitelisted players join in "
                + pluginConfig.getGuestGameMode().name() + " mode.");
    }

    @Override
    public void onDisable() {
        if (upgradeTask != null) {
            upgradeTask.cancel();
        }
        getLogger().info("Disabled.");
    }

    public void reload() {
        reloadConfig();
        pluginConfig = new PluginConfig(this);

        /* Re-evaluate all tracked guests after a config reload. Players whitelisted
         * while the config was being reloaded get upgraded now. Players still not
         * whitelisted remain in the registry with their game mode unchanged. */
        for (UUID uuid : registry.snapshot()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                registry.remove(uuid);
                continue;
            }

            if (player.isWhitelisted()) {
                registry.remove(uuid);
                player.setGameMode(pluginConfig.getUpgradeGameMode());
                player.sendMessage(colorize(pluginConfig.getUpgradeMessage()));
            }
        }

        getLogger().info("Configuration reloaded.");
    }

    /* Translates & color codes to section sign codes for sendMessage(String)
     * and kickPlayer(String). Kept here so every caller uses the same method. */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public GuestRegistry getRegistry()       { return registry; }
    public PluginConfig getPluginConfig()    { return pluginConfig; }
}
