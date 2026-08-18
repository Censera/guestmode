package xyz.censera.guestmode;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class AuthManager {
    private static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GuestMode plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Account> accounts = new HashMap<>();

    AuthManager(GuestMode plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "accounts.yml");
        data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        for (String raw : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                String hash = data.getString(raw + ".password");
                String salt = data.getString(raw + ".salt");
                if (hash != null && salt != null) {
                    accounts.put(uuid, new Account(hash, salt,
                            data.getString(raw + ".totp"), data.getBoolean(raw + ".totp-enabled")));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid account entry: " + raw);
            }
        }
    }

    boolean isRegistered(UUID uuid) {
        return accounts.containsKey(uuid);
    }

    boolean isAuthenticated(UUID uuid) {
        return plugin.getAuthenticated().contains(uuid);
    }

    void unregister(UUID uuid) {
        accounts.remove(uuid);
        data.set(uuid.toString(), null);
        save();
    }

    void register(Player player, String password, Consumer<String> result) {
        UUID uuid = player.getUniqueId();
        if (isRegistered(uuid)) {
            result.accept("already-registered");
            return;
        }
        hashAsync(password, hash -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            String[] parts = hash.split("\\$", 2);
            Account account = new Account(parts[1], parts[0], null, false);
            accounts.put(uuid, account);
            write(uuid, account);
            plugin.getAuthenticated().add(uuid);
            result.accept("ok");
        }));
    }

    void login(Player player, String password, String code, Consumer<String> result) {
        UUID uuid = player.getUniqueId();
        Account account = accounts.get(uuid);
        if (account == null) {
            result.accept("not-registered");
            return;
        }
        if (account.totpEnabled && !verifyTotp(account.totp, code)) {
            result.accept(code == null || code.isBlank() ? "2fa-required" : "invalid-2fa");
            return;
        }
        verifyPasswordAsync(password, account.salt, account.password, valid ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (valid) {
                        plugin.getAuthenticated().add(uuid);
                        result.accept("ok");
                    } else {
                        result.accept("invalid-password");
                    }
                }));
    }

    String beginTotp(Player player) {
        Account account = accounts.get(player.getUniqueId());
        if (account == null || !isAuthenticated(player.getUniqueId())) return null;
        if (account.totpEnabled) return null;
        account.totp = randomBase32(20);
        write(player.getUniqueId(), account);
        return account.totp;
    }

    boolean confirmTotp(Player player, String code) {
        Account account = accounts.get(player.getUniqueId());
        if (account == null || account.totp == null || !isAuthenticated(player.getUniqueId())) return false;
        if (!verifyTotp(account.totp, code)) return false;
        account.totpEnabled = true;
        write(player.getUniqueId(), account);
        return true;
    }

    boolean disableTotp(Player player, String code) {
        Account account = accounts.get(player.getUniqueId());
        if (account == null || !account.totpEnabled || !verifyTotp(account.totp, code)) return false;
        account.totp = null;
        account.totpEnabled = false;
        write(player.getUniqueId(), account);
        return true;
    }

    boolean hasTotp(UUID uuid) {
        Account account = accounts.get(uuid);
        return account != null && account.totpEnabled;
    }

    private void write(UUID uuid, Account account) {
        String path = uuid.toString();
        data.set(path + ".password", account.password);
        data.set(path + ".salt", account.salt);
        data.set(path + ".totp", account.totp);
        data.set(path + ".totp-enabled", account.totpEnabled);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save accounts.yml: " + e.getMessage());
        }
    }

    private static void hashAsync(String password, Consumer<String> callback) {
        Thread.startVirtualThread(() -> {
            try {
                byte[] salt = new byte[16];
                RANDOM.nextBytes(salt);
                byte[] hash = derive(password, salt);
                callback.accept(Base64.getEncoder().encodeToString(salt) + "$"
                        + Base64.getEncoder().encodeToString(hash));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Password hashing failed", e);
            }
        });
    }

    private static void verifyPasswordAsync(String password, String salt, String expected, Consumer<Boolean> callback) {
        Thread.startVirtualThread(() -> {
            try {
                byte[] actual = derive(password, Base64.getDecoder().decode(salt));
                byte[] wanted = Base64.getDecoder().decode(expected);
                callback.accept(MessageDigest.isEqual(actual, wanted));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                callback.accept(false);
            }
        });
    }

    private static byte[] derive(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String randomBase32(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return base32(value);
    }

    private static boolean verifyTotp(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{6}")) return false;
        long counter = System.currentTimeMillis() / 30_000L;
        for (long offset = -1; offset <= 1; offset++) {
            if (totp(secret, counter + offset).equals(code)) return true;
        }
        return false;
    }

    private static String totp(String secret, long counter) {
        try {
            byte[] key = base32Decode(secret);
            byte[] message = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int index = hash[hash.length - 1] & 0x0f;
            int value = ((hash[index] & 0x7f) << 24)
                    | ((hash[index + 1] & 0xff) << 16)
                    | ((hash[index + 2] & 0xff) << 8)
                    | (hash[index + 3] & 0xff);
            return "%06d".formatted(value % 1_000_000);
        } catch (GeneralSecurityException e) {
            return "";
        }
    }

    private static String base32(byte[] bytes) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(alphabet[(buffer >>> bits) & 31]);
            }
        }
        if (bits > 0) out.append(alphabet[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    private static byte[] base32Decode(String value) {
        byte[] out = new byte[value.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : value.toUpperCase().toCharArray()) {
            int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out[index++] = (byte) ((buffer >>> bits) & 0xff);
            }
        }
        return out;
    }

    String totpUri(Player player, String secret) {
        String issuer = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "GuestMode".getBytes(StandardCharsets.UTF_8));
        return "otpauth://totp/" + issuer + ":" + player.getName()
                + "?secret=" + secret + "&issuer=GuestMode&algorithm=SHA1&digits=6&period=30";
    }

    private static final class Account {
        String password;
        String salt;
        String totp;
        boolean totpEnabled;

        Account(String password, String salt, String totp, boolean totpEnabled) {
            this.password = password;
            this.salt = salt;
            this.totp = totp;
            this.totpEnabled = totpEnabled;
        }
    }
}
