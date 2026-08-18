package xyz.censera.guestmode;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Set;
import java.util.UUID;

public final class GuestMode extends JavaPlugin {
    private GuestRegistry registry;
    private PluginConfig pluginConfig;
    private UpgradeTask upgradeTask;
    private AuthManager auth;
    private final Set<UUID> authenticated = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        registry = new GuestRegistry();
        auth = new AuthManager(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);

        upgradeTask = new UpgradeTask(this);
        upgradeTask.start();

        GuestModeCommand executor = new GuestModeCommand(this);
        var adminCommand = getCommand("guestmode");
        if (adminCommand == null) {
            throw new IllegalStateException("Required command 'guestmode' is missing from plugin.yml");
        }
        adminCommand.setExecutor(executor);
        adminCommand.setTabCompleter(executor);

        AuthCommand authCommand = new AuthCommand(this);
        getCommand("register").setExecutor(authCommand);
        getCommand("login").setExecutor(authCommand);
        getCommand("2fa").setExecutor(authCommand);

        getLogger().info("Enabled on Paper 26.2.");
    }

    @Override
    public void onDisable() {
        if (upgradeTask != null) {
            upgradeTask.cancel();
            upgradeTask = null;
        }
        authenticated.clear();
        getLogger().info("Disabled.");
    }

    boolean isFloodgatePlayer(UUID uuid) {
        if (getServer().getPluginManager().getPlugin("floodgate") == null) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (RuntimeException e) {
            return false;
        }
    }

    FastLoginBukkit getFastLogin() {
        var plugin = getServer().getPluginManager().getPlugin("FastLogin");
        return plugin instanceof FastLoginBukkit fastLogin && fastLogin.isEnabled() ? fastLogin : null;
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
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', pluginConfig.getUpgradeMessage()));
            }
        }
        getLogger().info("Configuration reloaded.");
    }

    GuestRegistry getRegistry() { return registry; }
    PluginConfig getPluginConfig() { return pluginConfig; }
    AuthManager getAuth() { return auth; }
    Set<UUID> getAuthenticated() { return authenticated; }
}
