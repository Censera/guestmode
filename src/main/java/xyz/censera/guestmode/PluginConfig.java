package xyz.censera.guestmode;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/*
 * Typed wrapper around config.yml.
 * Rebuilt on each reload so callers always read the current values.
 */
public final class PluginConfig {

    private final String guestJoinMessage;
    private final String upgradeMessage;
    private final String broadcastOnUpgrade;
    private final GameMode guestGameMode;
    private final GameMode upgradeGameMode;
    private final boolean kickIfWhitelistEnabled;
    private final String kickMessage;

    public PluginConfig(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        guestJoinMessage       = cfg.getString("guest-join-message",    "&eWelcome, &f%player%&e! You are in Guest Mode. You can explore but not build or break.");
        upgradeMessage         = cfg.getString("upgrade-message",       "&aYou have been whitelisted! Switching you to Survival mode.");
        broadcastOnUpgrade     = cfg.getString("broadcast-on-upgrade",  "&6%player% &ahas been whitelisted and upgraded from Guest Mode!");
        kickIfWhitelistEnabled = cfg.getBoolean("kick-if-whitelist-enabled", false);
        kickMessage            = cfg.getString("kick-message",          "&cThis server is whitelisted. Contact an admin to be added.");

        guestGameMode   = parseGameMode(plugin, "guest-gamemode",  cfg.getString("guest-gamemode",  "ADVENTURE"), GameMode.ADVENTURE);
        upgradeGameMode = parseGameMode(plugin, "upgrade-gamemode", cfg.getString("upgrade-gamemode", "SURVIVAL"),  GameMode.SURVIVAL);
    }

    private static GameMode parseGameMode(JavaPlugin plugin, String key, String raw, GameMode fallback) {
        try {
            return GameMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid game mode ''{0}'' for key ''{1}'' — falling back to {2}.",
                    new Object[]{ raw, key, fallback.name() });
            return fallback;
        }
    }

    public String getGuestJoinMessage()       { return guestJoinMessage; }
    public String getUpgradeMessage()         { return upgradeMessage; }
    public String getBroadcastOnUpgrade()     { return broadcastOnUpgrade; }
    public GameMode getGuestGameMode()        { return guestGameMode; }
    public GameMode getUpgradeGameMode()      { return upgradeGameMode; }
    public boolean isKickIfWhitelistEnabled() { return kickIfWhitelistEnabled; }
    public String getKickMessage()            { return kickMessage; }
}
