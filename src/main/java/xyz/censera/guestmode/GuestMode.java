package xyz.censera.guestmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuestMode extends JavaPlugin {
    private static final long DIMENSION_GRACE_TICKS = 120L * 20L;
    private static final double MAX_DISTANCE_SQUARED = 200.0 * 200.0;

    private GuestRegistry registry;
    private PluginConfig pluginConfig;
    private UpgradeTask upgradeTask;
    private AuthManager auth;
    private TwoFactorSetupServer twoFactorSetupServer;
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> dimensionGrace = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        registry = new GuestRegistry();
        auth = new AuthManager(this);
        twoFactorSetupServer = new TwoFactorSetupServer(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new GuestProtectionListener(this), this);

        upgradeTask = new UpgradeTask(this);
        upgradeTask.start();

        GuestModeCommand executor = new GuestModeCommand(this);
        requireCommand("eyes").setExecutor(executor);
        requireCommand("eyes").setTabCompleter(executor);

        AuthCommand authCommand = new AuthCommand(this);
        requireCommand("register").setExecutor(authCommand);
        requireCommand("login").setExecutor(authCommand);
        requireCommand("2fa").setExecutor(authCommand);

        GuestCommand guestCommand = new GuestCommand(this);
        requireCommand("guest").setExecutor(guestCommand);

        getLogger().info("Eyes enabled.");
    }

    @Override
    public void onDisable() {
        if (upgradeTask != null) {
            upgradeTask.cancel();
            upgradeTask = null;
        }
        dimensionGrace.values().forEach(BukkitTask::cancel);
        dimensionGrace.clear();
        if (twoFactorSetupServer != null) {
            twoFactorSetupServer.stop();
            twoFactorSetupServer = null;
        }
        authenticated.clear();
        getLogger().info("Eyes disabled.");
    }

    void enterGuest(Player player) {
        UUID uuid = player.getUniqueId();
        if (registry.contains(uuid)) {
            return;
        }

        registry.add(uuid);
        player.setGameMode(pluginConfig.getGuestGameMode());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&', pluginConfig.getGuestJoinMessage().replace("%player%", player.getName())));

        if (isNormalWorld(player.getWorld())) {
            cancelDimensionGrace(uuid);
        } else {
            startDimensionGrace(player);
        }
    }

    void exitGuest(Player player) {
        UUID uuid = player.getUniqueId();
        cancelDimensionGrace(uuid);
        registry.remove(uuid);
        player.setGameMode(pluginConfig.getUpgradeGameMode());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', pluginConfig.getUpgradeMessage()));
    }

    void handleGuestWorldChange(Player player) {
        if (!registry.contains(player.getUniqueId())) {
            return;
        }
        if (isNormalWorld(player.getWorld())) {
            cancelDimensionGrace(player.getUniqueId());
        } else {
            startDimensionGrace(player);
        }
    }

    void moveGuestToSafeLocation(Player player) {
        Location target = player.getBedSpawnLocation();
        if (!isValidGuestLocation(target)) {
            World world = firstNormalWorld();
            target = world == null ? null : safeSpawn(world);
        }

        if (target == null) {
            throw new IllegalStateException("No safe guest location is available for " + player.getName());
        }

        player.teleport(target);
    }

    private Location safeSpawn(World world) {
        Location spawn = world.getSpawnLocation();
        if (isValidGuestLocation(spawn)) {
            return spawn;
        }

        int baseX = spawn.getBlockX();
        int baseZ = spawn.getBlockZ();
        for (int radius = 1; radius <= 16; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
                        continue;
                    }
                    int blockX = baseX + x;
                    int blockZ = baseZ + z;
                    int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
                    Location candidate = new Location(world, blockX + 0.5, y, blockZ + 0.5);
                    if (isValidGuestLocation(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidGuestLocation(Location location) {
        if (location == null || !isNormalWorld(location.getWorld()) || !withinGuestBoundary(location)) {
            return false;
        }
        return !isDangerous(location)
                && location.getBlock().isPassable()
                && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void startDimensionGrace(Player player) {
        UUID uuid = player.getUniqueId();
        if (dimensionGrace.containsKey(uuid)) {
            return;
        }

        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            dimensionGrace.remove(uuid);
            if (!player.isOnline() || !registry.contains(uuid) || isNormalWorld(player.getWorld())) {
                return;
            }
            moveGuestToSafeLocation(player);
        }, DIMENSION_GRACE_TICKS);
        dimensionGrace.put(uuid, task);
    }

    private void cancelDimensionGrace(UUID uuid) {
        BukkitTask task = dimensionGrace.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isNormalWorld(World world) {
        return world != null && world.getEnvironment() == World.Environment.NORMAL;
    }

    private boolean withinGuestBoundary(Location location) {
        World world = location.getWorld();
        if (!isNormalWorld(world)) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        return dx * dx + dz * dz <= MAX_DISTANCE_SQUARED;
    }

    private boolean isDangerous(Location location) {
        String type = location.getBlock().getType().toString();
        String above = location.clone().add(0, 1, 0).getBlock().getType().toString();
        return location.getBlock().isLiquid()
                || type.contains("FIRE")
                || type.contains("MAGMA")
                || type.contains("CAMPFIRE")
                || above.contains("FIRE")
                || location.getY() < location.getWorld().getMinHeight() + 1;
    }

    private World firstNormalWorld() {
        return Bukkit.getWorlds().stream()
                .filter(this::isNormalWorld)
                .findFirst()
                .orElse(null);
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
            getLogger().warning("Floodgate integration failed; requiring normal authentication for " + uuid + ": " + e.getMessage());
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
            getLogger().warning("FastLogin integration failed; requiring normal authentication for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    String startTwoFactorSetup(Player player, String secret) throws java.io.IOException {
        return twoFactorSetupServer.start(new TwoFactorSetupServer.PlayerSetup(
                player.getUniqueId(), player.getName(), secret, auth.totpUri(player, secret)));
    }

    void reload() {
        reloadConfig();
        pluginConfig = new PluginConfig(this);
        getLogger().info("Configuration reloaded.");
    }

    private org.bukkit.command.PluginCommand requireCommand(String name) {
        org.bukkit.command.PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Required command '" + name + "' is missing from plugin.yml");
        }
        return command;
    }

    GuestRegistry getRegistry() { return registry; }
    PluginConfig getPluginConfig() { return pluginConfig; }
    AuthManager getAuth() { return auth; }
    Set<UUID> getAuthenticated() { return authenticated; }
}
