# MySKMPlugin — low-config Paper starter

A copyable Paper starter that removes repetitive SKM integration work while leaving game behavior to the plugin author. It includes multi-action YAML loading, validation, stable IDs, owner-scoped lifecycle cleanup, safe reload with rollback, admin commands, permission/level fields, a holdable example route, and unit tests. The actual ability effects remain TODOs by design.

## Included scaffold

- `ActionConfigLoader`: parses all action entries and validates required fields, types, duplicate IDs, key tokens, cooldown and level bounds before changing live registrations.
- `ActionManager`: owns this plugin's registrations, avoids needless re-syncs, and restores the last working action set if SKM rejects a replacement.
- `/myskmplugin status`: lists active IDs, handlers, defaults and holdable state.
- `/myskmplugin reload` (operator by default): reloads `config.yml`; invalid config leaves the previously active actions in place when rollback succeeds.
- `ActionExecutionService` routes the configured `handler` key to server-side code. The sample includes `ability` and `charge` routes.
- Holdable state has `PRESS`/`RELEASE` handling plus player-quit/plugin-disable cleanup hooks.
- An optional `PlayerActionEvent` hook is included for shared checks or cancellation.
- JUnit tests cover config parsing, duplicate/invalid entries, action replacement rollback and cleanup.

## Build and test

Requires JDK 25. From the SKM repository root:

```bash
./gradlew :plugin-template:clean :plugin-template:test :plugin-template:build
```

The artifact is `examples/plugin-template/build/libs/my-skm-plugin-0.1.0-SNAPSHOT.jar`. The API is `compileOnly` and is not bundled into the plugin JAR.

To build this directory as a standalone project, first install the matching SKM API into Maven Local from the SKM checkout:

```bash
cd /path/to/skm
./gradlew :skm-api:publishToMavenLocal
cd examples/plugin-template
./gradlew clean test build
```

Alternatively, configure GitHub Packages credentials with `read:packages` using `GITHUB_ACTOR` and `GITHUB_TOKEN` (or Gradle properties `gpr.user` and `gpr.key`). Never commit tokens.

## Configure actions

Edit `src/main/resources/config.yml` before the first run or `plugins/MySKMPlugin/config.yml` later. One YAML item represents one client key action:

```yaml
actions:
  - id: yourplugin.dash           # stable, globally unique ID; do not rename after release
    enabled: true
    handler: dash                 # route implemented in MySKMPlugin.execute()
    display-name: "Dash"
    default-key: "key.keyboard.g"
    cooldown-ms: 750
    category: "YourPlugin"
    holdable: false
    permission: "yourplugin.dash"
    required-level: 0
```

Add another list item to register another action. `enabled: false` keeps the entry in config but does not register it. Available API bounds are: action ID 1–64 characters with lowercase letters/digits/`._-`; display name at most 128 characters; category at most 64; cooldown 0–3,600,000 ms; required level 0–1,000,000. Key tokens look like `key.keyboard.g` or `key.mouse.1`. A positive `required-level` requires a `LevelRequirementService` from a progression plugin, otherwise SKM rejects the input.

`handler` is a plugin-local route, not an SKM field. Add a matching name to `SUPPORTED_HANDLERS` and a matching `case` to the `execute()` switch, then implement that route on the server. Unknown routes are rejected during config loading instead of silently denying every input. For a custom permission, also declare the same node in `plugin.yml`.

## Customize safely

1. Rename the root project, package/class, plugin name, `main`, command, and permission prefixes.
2. Keep each action ID stable between releases. The ID associates the player's local per-server key override; it does **not** persist the server registration.
3. Add config entries and route cases; then run `/myskmplugin reload`. The old actions are retained if parsing or registration of the replacement fails.
4. In every handler, validate player/game state on the server before applying an effect. Never trust a key press as proof that a target, resource, distance, or cooldown is valid.
5. For `holdable: true`, handle both edges. Start work on `PRESS`, stop it on `RELEASE`, and cancel per-player tasks/resources on quit and plugin disable. SKM does not automatically cancel tasks created by a consumer plugin.
6. Use `/myskmplugin status` to inspect the live action set. The management command requires `myskmplugin.admin` (operator by default); per-action permissions are checked by SKM before the handler runs.

The template registers actions during `onEnable()` and removes them during `onDisable()`. This runtime registration is intentionally in memory. If a user-controlled enable state must survive restart, persist that desired state in your plugin's own config or database and re-register during startup. For a small global toggle, YAML is simpler than a database; the SKM API itself stays storage-agnostic.

Install the matching SKM server plugin and this plugin on Paper; players also need the compatible Fabric Loader, Fabric API and SKM client mod. Full API semantics and security guidance are in the [Plugin Developer Guide](../../docs/PLUGIN-DEVELOPMENT.md).
