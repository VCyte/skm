# MySKMPlugin — copyable Paper starter

A minimal, buildable starting project for a Paper plugin that registers its own SKM key at runtime. It shows the parts plugin authors need to wire together: compile-only API dependency, `depend: [SKM]`, configuration-backed `ActionDefinition`, lifecycle cleanup, and a server-side `ActionExecutionService` handler. The example message is intentionally a placeholder, not gameplay logic.

## Build inside the SKM checkout

Requires JDK 25. From the SKM repository root:

```bash
bash ./gradlew :plugin-template:build
```

The artifact is `examples/plugin-template/build/libs/my-skm-plugin-0.1.0-SNAPSHOT.jar`.

## Build after copying this directory elsewhere

First install the matching API into your local Maven repository from the SKM checkout:

```bash
cd /path/to/skm
bash ./gradlew :skm-api:publishToMavenLocal
cd examples/plugin-template
bash ./gradlew clean build
```

Alternatively, configure GitHub Packages credentials with `read:packages` using `GITHUB_ACTOR` and `GITHUB_TOKEN` (or Gradle properties `gpr.user` and `gpr.key`). Do not commit tokens. The API is declared as `compileOnly`: do not shade or bundle SKM API classes into your plugin JAR. At runtime the SKM server plugin provides them.

## Customize before use

1. Rename `rootProject.name`, the Java package/class, `name`, and `main` in `plugin.yml`.
2. Choose a unique, stable action ID in `config.yml`, such as `yourplugin.ability`. Keep it stable across releases so player key overrides remain associated with the action.
3. Change the display name, default key, cooldown, category, permission, and holdable setting to match your feature.
4. Replace the TODO in `MySKMPlugin.execute` with server-side validation and the actual game effect.
5. Add `plugin.yml` commands, permissions, listeners, and services required by your own plugin.

`required-level` greater than zero requires a `LevelRequirementService` provider from a progression plugin; otherwise SKM denies the action. `holdable: true` enables both press and release input. `SUCCESS` on a press starts the configured cooldown; other results do not.

## Install and verify

Install `skm-server-1.2.1.jar` and the built plugin JAR in Paper's `plugins/` folder. Players also need Fabric Loader, Fabric API, and `skm-client-1.2.1-26.2-fabric.jar`. The plugin declares `depend: [SKM]`, so Paper loads SKM first. On startup, the starter registers its action; on disable, it unregisters owned actions. Test the key in Minecraft's Controls screen and confirm that only server-side code applies effects.

For the full API contract, thread rules, permissions, lifecycle, events, and troubleshooting, read [Plugin Developer Guide](../../docs/PLUGIN-DEVELOPMENT.md). The companion [ExampleWithSKM](../examplewithskm/README.md) demonstrates command-driven add/remove behavior.
