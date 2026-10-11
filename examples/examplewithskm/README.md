# ExampleWithSKM

Paper consumer plugin showing how to add and remove an SKM key at runtime. It requires the matching `tmin.click:skm-api` version and does not need an `actions.yml` entry.

## What it demonstrates

- `/examplewithskm add` registers the plugin-owned action `examplewithskm.ping` through `ActionRegistrationService`; connected compatible clients receive the binding on the next server tick.
- `/examplewithskm remove` unregisters the action and saves the disabled state.
- The desired enable state is persisted in `plugins/ExampleWithSKM/config.yml`. If `action.enabled: true`, the plugin re-registers its code-owned action at startup or after a plugin reload.
- The action ID is a stable identifier, not the storage location for the server registration. Player key overrides are stored separately on each client under `.minecraft/config/skm/servers/<server-signature>.json`, keyed by server signature and action ID. If the same ID is registered again, that player's key choice can be restored.
- Pressing the default `J` key calls this plugin's `ActionExecutionService` handler and sends a private confirmation only to the player who pressed it. Replace the sample response with server-side game logic.
- SKM automatically removes all actions owned by this plugin when it is disabled; the example then restores the desired state when it is enabled again.

The command permission defaults to operators. The example action is non-holdable and has a 750 ms server cooldown.

## Persistence and database choice

`ActionRegistrationService` keeps live registrations in memory; it cannot persist a Java handler or plugin object. A plugin should register its actions again during `onEnable()`. This example stores only the user's global on/off choice in its own YAML config, which is simpler and safer than introducing a database for one boolean.

Use storage owned by your plugin if you need saved settings. A YAML config is suitable for a small global toggle. Consider SQLite or a server database only for larger or per-player state, queries, or coordination across multiple servers; SKM itself should not own that application data.

## Build from the SKM repository

Java 25 is required. From the repository root:

```bash
./gradlew clean :examplewithskm:build
```

The built plugin is `examples/examplewithskm/build/libs/examplewithskm.jar`.

## Build this project standalone

First install the matching API artifact to the local Maven repository from the SKM checkout:

```bash
./gradlew :skm-api:publishToMavenLocal
cd examples/examplewithskm
./gradlew -PskmVersion=1.2.2 clean build
```

Alternatively, configure GitHub Packages credentials with `read:packages`; see [the SKM API guide](https://github.com/VCyte/skm/blob/main/docs/API.md). The generated JAR name remains `examplewithskm.jar` for every SKM version.

## Install and try

1. Install the matching SKM server plugin and `examplewithskm.jar` in Paper's `plugins/` folder.
2. Ensure players have the compatible Fabric SKM client mod.
3. Start the server and run `/examplewithskm add` as an operator or from the console.
4. Connect with an SKM client and press `J`; the test handler replies only to that player.
5. Restart or reload the plugin; the action is restored from `plugins/ExampleWithSKM/config.yml`.
6. Run `/examplewithskm remove`; the action disappears and remains disabled after restart.

The v1.1.0-compatible sample is kept separately in `../examplewithskm-v1.1.0`. That version predates runtime registration, so its action is configured statically in SKM's `actions.yml`.
