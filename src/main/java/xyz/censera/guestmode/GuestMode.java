package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
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
        getServer().getPluginManager().registerEvents(new GuestProtectionListener(this), this);

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

        GuestCommand guestCommand = new GuestCommand(this);
        getCommand("guest").setExecutor(guestCommand);

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

    void enterGuest(Player player) {
        UUID uuid = player.getUniqueId();
        if (registry.snapshot().contains(uuid)) {
            return;
        }

        registry.add(uuid);
        player.setGameMode(pluginConfig.getGuestGameMode());
        player.setFoodLevel(20);
        player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&', pluginConfig.getGuestJoinMessage().replace("%player%", player.getName())));
    }

    void exitGuest(Player player) {
        UUID uuid = player.getUniqueId();
        registry.remove(uuid);
        player.setGameMode(pluginConfig.getUpgradeGameMode());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', pluginConfig.getUpgradeMessage()));
    }

    boolean isFloodgatePlayer(UUID uuid) {
        if (getServer().getPluginManager().getPlugin("floodgate") == null) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, uuid));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    boolean isPremiumPlayer(UUID uuid) {
        if (getServer().getPluginManager().getPlugin("FastLogin") == null) {
            return false;
        }

        try {
            Class<?> pluginClass = Class.forName("com.github.games647.fastlogin.bukkit.FastLoginBukkit");
            Object plugin = getServer().getPluginManager().getPlugin("FastLogin");
            if (plugin == null || !pluginClass.isInstance(plugin)) {
                return false;
            }

            Method getStatus = pluginClass.getMethod("getStatus", UUID.class);
            Object status = getStatus.invoke(plugin, uuid);
            return status != null && "PREMIUM".equals(status.toString());
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().fine("FastLogin integration unavailable; using normal authentication fallback.");
            return false;
        }
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
