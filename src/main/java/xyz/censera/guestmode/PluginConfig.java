package xyz.censera.guestmode;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class PluginConfig {

    private final String guestJoinMessage;
    private final String upgradeMessage;
    private final String broadcastOnUpgrade;
    private final GameMode guestGameMode;
    private final GameMode upgradeGameMode;
    private final boolean kickIfWhitelistEnabled;
    private final String kickMessage;

    PluginConfig(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        guestJoinMessage = requiredString(cfg, "guest-join-message");
        upgradeMessage = requiredString(cfg, "upgrade-message");
        broadcastOnUpgrade = requiredString(cfg, "broadcast-on-upgrade");
        kickMessage = requiredString(cfg, "kick-message");
        kickIfWhitelistEnabled = cfg.getBoolean("kick-if-whitelist-enabled");
        guestGameMode = parseGameMode(cfg, "guest-gamemode", GameMode.ADVENTURE, GameMode.SPECTATOR);
        upgradeGameMode = parseGameMode(cfg, "upgrade-gamemode", GameMode.SURVIVAL, GameMode.CREATIVE);
    }

    private static String requiredString(FileConfiguration cfg, String key) {
        String value = cfg.getString(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required configuration: " + key);
        }
        return value;
    }

    private static GameMode parseGameMode(
            FileConfiguration cfg,
            String key,
            GameMode firstAllowed,
            GameMode secondAllowed) {
        String raw = requiredString(cfg, key);
        try {
            GameMode mode = GameMode.valueOf(raw.toUpperCase());
            if (mode != firstAllowed && mode != secondAllowed) {
                throw invalidGameMode(key, raw);
            }
            return mode;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Invalid game mode for ")) {
                throw e;
            }
            throw invalidGameMode(key, raw);
        }
    }

    private static IllegalArgumentException invalidGameMode(String key, String value) {
        return new IllegalArgumentException(
                "Invalid game mode for " + key + ": " + value);
    }

    String getGuestJoinMessage() {
        return guestJoinMessage;
    }

    String getUpgradeMessage() {
        return upgradeMessage;
    }

    String getBroadcastOnUpgrade() {
        return broadcastOnUpgrade;
    }

    GameMode getGuestGameMode() {
        return guestGameMode;
    }

    GameMode getUpgradeGameMode() {
        return upgradeGameMode;
    }

    boolean isKickIfWhitelistEnabled() {
        return kickIfWhitelistEnabled;
    }

    String getKickMessage() {
        return kickMessage;
    }
}
