# Eyes

Eyes is a Paper plugin for offline-mode servers that combines account authentication with world protection.

Authentication answers **who are you?** World protection answers **what are you trusted to do?** They are separate checks that work together.

A public server can accept strangers without giving them access to the real world. New and untrusted players enter Guest Mode and remain there until they are both authenticated and trusted through the server whitelist.

## Authentication

Cracked/offline-mode players use:

- `/register <password>`
- `/login <password> [2fa-code]`
- `/2fa enable`
- `/2fa confirm <code>`
- `/2fa disable <code>`

Passwords use salted PBKDF2-HMAC-SHA256. 2FA uses standard TOTP. Accounts are stored in `plugins/Eyes/accounts.yml` and keyed by UUID.

Premium Java players can authenticate automatically through the optional FastLogin integration. Floodgate players are authenticated through Floodgate. These integrations provide identity proof; they do not grant world trust by themselves.

## Guest Mode

Untrusted players are kept in Guest Mode until they are authenticated and whitelisted.

Guests:

- Cannot use normal commands before authentication.
- Cannot damage entities or be damaged by entities.
- Cannot interact with the world normally.
- Cannot leave a 200-block horizontal radius around the configured guest world spawn.
- Cannot enter the Nether or End.
- Cannot use cross-world teleports.

Players who refuse to register or log in remain guests. Players who are authenticated but not whitelisted also remain guests.

Whitelisting is a **trust decision**, not an authentication bypass. A whitelisted cracked player still has to log in to their account.

## Integrations

Geyser/Floodgate, ViaVersion, and ViaBackwards are treated as server-side compatibility layers. Eyes does not hook their packet internals.

FastLogin is optional. Eyes accepts only its verified `PREMIUM` status as proof of premium authentication.

## Build

Requires Java 25 and Maven 3.8+.

```sh
mvn package -DskipTests
```

The resulting `Eyes-4.0.1.jar` goes in `plugins/`.

## License

MIT
