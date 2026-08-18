package xyz.censera.guestmode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class AuthCommand implements CommandExecutor {
    private final GuestMode plugin;

    AuthCommand(GuestMode plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        return switch (command.getName().toLowerCase()) {
            case "register" -> register(player, args);
            case "login" -> login(player, args);
            case "2fa" -> twoFactor(player, args);
            default -> false;
        };
    }

    private boolean register(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /register <password>");
            return true;
        }
        if (args[0].length() < 8) {
            player.sendMessage(ChatColor.RED + "Password must be at least 8 characters.");
            return true;
        }
        plugin.getAuth().register(player, args[0], result -> {
            switch (result) {
                case "ok" -> player.sendMessage(ChatColor.GREEN + "Registered and logged in. You remain in Guest Mode until whitelisted.");
                case "already-registered" -> player.sendMessage(ChatColor.RED + "You are already registered.");
                default -> player.sendMessage(ChatColor.RED + "Registration failed.");
            }
        });
        return true;
    }

    private boolean login(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /login <password> [2fa-code]");
            return true;
        }
        plugin.getAuth().login(player, args[0], args.length == 2 ? args[1] : null, result -> {
            switch (result) {
                case "ok" -> player.sendMessage(ChatColor.GREEN + "Logged in. You remain in Guest Mode until whitelisted.");
                case "not-registered" -> player.sendMessage(ChatColor.RED + "You are not registered. Use /register <password>.");
                case "2fa-required" -> player.sendMessage(ChatColor.RED + "Your account requires a 2FA code.");
                case "invalid-2fa" -> player.sendMessage(ChatColor.RED + "Invalid 2FA code.");
                default -> player.sendMessage(ChatColor.RED + "Invalid password.");
            }
        });
        return true;
    }

    private boolean twoFactor(Player player, String[] args) {
        if (!plugin.getAuth().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be logged in.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("enable")) {
            String secret = plugin.getAuth().beginTotp(player);
            if (secret == null) {
                player.sendMessage(ChatColor.RED + "2FA is already enabled.");
                return true;
            }

            String uri = plugin.getAuth().totpUri(player, secret);
            player.sendMessage(ChatColor.GREEN + "2FA secret: " + ChatColor.WHITE + secret);
            player.sendMessage(ChatColor.YELLOW + "Add it to your authenticator, then use /2fa confirm <code>.");
            player.sendMessage(Component.text("[Open 2FA setup link]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.openUrl(uri))
                    .hoverEvent(HoverEvent.showText(Component.text("Open the 2FA setup URI"))));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            if (plugin.getAuth().confirmTotp(player, args[1])) {
                player.sendMessage(ChatColor.GREEN + "2FA enabled.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid 2FA code.");
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("disable")) {
            if (plugin.getAuth().disableTotp(player, args[1])) {
                player.sendMessage(ChatColor.GREEN + "2FA disabled.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid 2FA code.");
            }
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /2fa <enable|confirm|disable> [code]");
        return true;
    }
}
