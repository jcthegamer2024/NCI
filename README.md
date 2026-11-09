# NCI (Nexo Creative Inventory)

NCI is a client-side Fabric mod that integrates with the Nexo plugin to show Nexo-defined items and blocks directly in the vanilla Creative inventory. It removes the need to browse `/nexo inventory` for most Creative workflow use cases.

NCI updates dynamically when the server sends refreshed registry data (for example, after `/nexo reload`).

## Features

- Adds Nexo item groups to Creative inventory tabs.
- Adds synced Nexo items to the Creative search tab.
- Refreshes tab contents automatically after server-side Nexo registry changes.
- Works client-side only (server runs Nexo plugin; client runs this mod).

## Installation

1. Install the appropriate Fabric Loader for the selected NCI version.
2. Download the appropriate Fabric API for the selected NCI version.
3. Download and place the NCI `.jar` in your client `mods` folder, along with the Fabric API `.jar`.
4. Join a server running Nexo `1.21+`.
5. Ensure the player account has the `nexo.command.inventory` permission node, then use `/n rl items` to rescan and resend items to NCI clients.

## Server Requirements

- Nexo inventory sync must be enabled (default behavior for Nexo `1.21+`).
- The player must have permission node `nexo.command.inventory`.
- The feature is intended for Creative users.

## Usage

1. Join a compatible multiplayer server.
2. Enter Creative mode.
3. Open Creative inventory and use the page selector to move between vanilla tabs and Nexo tabs.
4. Use the Creative search tab to find synced Nexo items quickly.

## Versioning

Release artifacts are named with both mod version and minecraft version values:

- `NCI-v<mod_version>-<minecraft_version>.jar`

## Protocol and Behavior Notes

- Client/server registry sync uses protocol negotiation by version range.
- Client advertises its minimum and maximum supported protocol versions.
- Server responds with the highest mutually supported protocol version.
- If there is no shared protocol version, sync is rejected as incompatible.
- Permission denial is handled separately and also prevents sync.

## Troubleshooting

- No Nexo tabs visible:
  - Confirm server is running Nexo `1.21+`.
  - Confirm your client is in Creative mode.
  - Confirm your account has `nexo.command.inventory`.
  - Confirm Fabric Loader/Fabric API and Minecraft versions match this release.
- Items outdated after reload:
  - Reconnect if the server did not push a fresh registry packet.
- Mod loads but nothing syncs:
  - Check client logs for Nexo handshake/protocol warnings.
- NCI armour looks vanilla but the same item from `/nexo inventory` works:
  - Hold each version in your main hand and run the client command `/nci inspect`.
  - This shows the held and matching cached equipment components in chat and
    writes their complete component patches to `logs/latest.log` (search for
    `NCI inspection`). Compare both before assuming an equipment asset ID.
  - If the cached patch lacks `minecraft:equippable` while the working server
    item includes it, the registry sender must serialize the complete item,
    including its component patch. Sending only the base material, name, item
    model and Nexo ID leaves the client with vanilla equipment defaults. Other
    components such as `minecraft:repairable` must also be included; an item model
    ID is not a substitute for an equipment asset ID.

## Development

This branch targets Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API
**0.165.0+26.3**, and **Java 25** (including the Gradle JVM).

Build locally with:

```bash
./gradlew clean build
```

Output artifacts are written to `build/libs/`.

Run the creative search regression test with `./gradlew runSearchTest`. It launches
a separate test client and temporary singleplayer world, checking vanilla name
and tag searches, Nexo names, repeated syncs, and removal of old search entries.
Test code is excluded from release jars.
The test also checks cached tab icons and recovery from a session reset while a
Nexo tab is selected in an open creative inventory.
It also covers closing on a late page's shared Search tab, resetting the session
while the screen is closed, and reopening with vanilla category tabs visible.
To check ModernFix compatibility, supply its local jar with
`./gradlew runSearchTest -PnciModernFixJar="/path/to/modernfix.jar"`.

### Migration from 1.21.11

- Uses unobfuscated Minecraft names and Loom 1.17.21 with Gradle 9.6.0;
  Yarn mappings and remapping tasks are no longer used.
- Migrates creative tab registration and networking to the renamed Fabric APIs,
  and packet buffers/codecs to the current Minecraft APIs.
- Uses an `ItemStackTemplate` for the startup icon because item stacks require a
  loaded world since 26.1. Synced stacks are still decoded with the connection's
  registry-aware stream codec.
- Forces a creative contents rebuild after sync even when vanilla's cached
  display parameters are unchanged, refreshing search contents and pagination.
- Rebuilds the session's name and tag search indexes after updating tab contents.
  This prevents an early NCI refresh from leaving creative search empty for both
  vanilla and Nexo items.
- Clears synced categories at play-session initialization and disconnect, rejects
  queued data from old connections, and rebuilds even before the new server sends
  Nexo data. Tab icons and an open creative screen are refreshed too, returning
  to a vanilla tab if the previous server's selected category no longer exists.
- Checks the retained creative page when opening the screen as well, preventing
  an old server's shared Search/Operator tab from reopening on an invalid page.
- Invalidates ModernFix's per-tab contents cache during forced registry refreshes,
  so an empty pre-sync inventory cannot keep all Nexo categories hidden.
- Accumulates protocol 1 category packets in both legacy and versioned envelopes,
  and combines repeated category IDs rather
  than keeping only the last packet. Up to 256 categories have reserved tab slots;
  empty slots are hidden by the creative inventory.
- Reviewed the 26.2 GUI/rendering changes and 26.3 SDL, registry, fuel, brewing,
  and world generation changes. NCI does not implement these systems directly.

The Nexo envelope protocol remains version 1. Embedded item stacks use Minecraft's
version-specific network format: use a Nexo server compatible with 26.3. Protocol
negotiation alone does not translate item components from a 1.21.11 server, and
cross-version proxies must translate this custom payload explicitly.
Malformed or partially decoded Nexo registry packets are discarded completely,
and NCI disables sync for that play session instead of disconnecting the client.
Vanilla inventory remains available. A new play session resets this protection.
This is graceful rejection, not translation of 1.21.11 item data into 26.3 data.

Protocol 1 packets have no end-of-snapshot marker. Their contents accumulate until
a new play session, so reconnect after removing items/categories to clear stale
entries. A versioned envelope (including server 1.1.1) can still contain only one
category chunk; the server version does not indicate a complete snapshot.

Migration references: [26.1](https://fabricmc.net/2026/03/14/261.html),
[26.2](https://fabricmc.net/2026/06/15/262.html), and
[26.3](https://fabricmc.net/2026/09/15/263.html).

Before release, verify on a compatible Nexo server: initial sync, creative search,
`/n rl items` while the inventory is open, permission denial, and reconnecting to
a different server. A successful build and client startup do not validate these
server interactions.

## Credits and Distribution

Suggested/developed by [ZoeWithTheE](https://github.com/ZoeWithTheE) and implemented by [Boy0000](https://github.com/Boy0000).

Public release channels:

- [Modrinth](https://modrinth.com/mod/nci)
- [GitHub](https://github.com/ZoeWithTheE/NCI)
- [Patreon Early Access](https://www.patreon.com/cw/MNIZ/membership)

## License

This repository is currently under a proprietary license. See the [LICENSE](https://github.com/ZoeWithTheE/NCI/blob/master/LICENSE) agreement for usage and redistribution terms.
