# GuestMode

A small Paper plugin for offline-mode servers. Unwhitelisted players enter Adventure mode, while registered players use password authentication.

Targets Paper 26.2 and Java 25.

Authentication is deliberately local:

- `/register <password>`
- `/login <password> [2fa-code]`
- `/2fa enable`
- `/2fa confirm <code>`
- `/2fa disable <code>`
- Passwords use salted PBKDF2-HMAC-SHA256.
- 2FA uses standard TOTP.
- Accounts are stored in `plugins/GuestMode/accounts.yml`.

Bedrock players detected by Floodgate skip authentication. Premium Java auto-login is supported through the optional FastLogin integration. FastLogin performs the actual Mojang session verification; GuestMode only consumes its verified premium status. This is necessary on an offline-mode server because a Paper plugin cannot prove that an arbitrary offline-mode client owns a premium Minecraft account by checking its username alone.

Geyser/Floodgate, ViaVersion, and ViaBackwards are treated as server-side compatibility layers. GuestMode does not hook their packet internals, so protocol translation does not become a maintenance dependency.

## Build

Requires Java 25 and Maven 3.8+.

```sh
mvn package -DskipTests
```

The resulting `GuestMode-2.0.0.jar` goes in `plugins/`.

## Guest mode

Unwhitelisted players join in the configured guest game mode. If an administrator adds them to the whitelist while they are online, GuestMode detects it once per second and upgrades them.

## Compatibility

Paper 26.2 is the target. Geyser/Floodgate, ViaVersion, and ViaBackwards require no direct packet hooks from this plugin. Floodgate is an optional runtime dependency and is detected automatically.

FastLogin is optional. When installed, GuestMode accepts only its `PREMIUM` status as proof of premium authentication.

## License

MIT
