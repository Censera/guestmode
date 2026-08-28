# Ce’s Eye

Ce’s Eye is a Paper plugin for offline-mode servers that separates player authentication from player trust.

## Build

```bash
# Build the plugin without running the test suite
mvn package -DskipTests
```

Requires Java 25 and Maven 3.8 or newer.

The build produces `Eyes-4.0.1.jar`. Copy it to the server's `plugins/` directory.

## Authentication

Offline-mode players authenticate with:

```text
/register <password>
/login <password> [2fa-code]
/2fa enable
/2fa confirm <code>
/2fa disable <code>
```

Ce’s Eye stores accounts in `plugins/Eyes/accounts.yml`.

Passwords use salted PBKDF2-HMAC-SHA256. 2FA uses TOTP.

FastLogin provides optional premium authentication. Floodgate provides Bedrock authentication through Geyser.

Authentication establishes identity. It does not establish trust.

## Guest mode

Players remain in Guest Mode until they are both authenticated and trusted through the server whitelist.

Guests cannot:

- use normal commands before authentication
- damage or be damaged by entities
- interact with the world normally
- move more than 200 blocks horizontally from the configured guest spawn
- enter the Nether or End
- use cross-world teleports

A player who authenticates without being whitelisted remains a guest.

A whitelisted offline-mode player still has to authenticate.

## Commands

| Command | Purpose |
|---|---|
| `/eyes reload` | Reload Ce’s Eye configuration |
| `/eyes list` | List relevant player state |
| `/eyes kick-guests` | Kick players currently in Guest Mode |
| `/guest unstuck` | Move a guest out of an invalid position |
| `/guest nudge` | Move a guest back toward the guest area |
| `/register <password>` | Create an offline-mode account |
| `/login <password> [2fa-code]` | Authenticate an account |
| `/2fa <enable\|confirm\|disable> [code]` | Manage TOTP authentication |

The `eyes.admin` permission grants administrative commands.

The `eyes.bypass` permission bypasses authentication and Guest Mode restrictions.

## Integrations

Ce’s Eye supports the following optional integrations:

- `FastLogin`
- `Floodgate`
- `Geyser-Spigot`
- `ViaVersion`
- `ViaBackwards`

Ce’s Eye does not hook the packet internals of Geyser, Floodgate, ViaVersion, or ViaBackwards.

## Requirements

- Paper `26.2` or newer
- Java `25`
- Maven `3.8` or newer for building

## License

MIT
