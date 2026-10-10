# ExampleWithSKM

Paper consumer plugin showing how to add and remove an SKM key at runtime. It requires the matching `tmin.click:skm-api` version and does not need an `actions.yml` entry.

## What it demonstrates

- `/examplewithskm add` registers `examplewithskm.ping` through `ActionRegistrationService`; connected compatible clients receive the binding immediately.
- `/examplewithskm remove` removes the action and its binding from connected clients.
- Pressing the default `J` key calls this plugin's `ActionExecutionService` handler and sends a private confirmation only to the player who pressed it. Replace the sample response with server-side game logic.
- SKM automatically removes all actions owned by this plugin when it is disabled.

The command permission defaults to operators. The example action is non-holdable and has a 750 ms server cooldown.

## Build from the SKM repository

Java 25 is required. From the repository root:

```bash
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean :examplewithskm:build
```

The built plugin is `examples/examplewithskm/build/libs/examplewithskm.jar`.

## Build this project standalone

First install the matching API artifact to the local Maven repository from the SKM checkout:

```bash
./gradlew :skm-api:publishToMavenLocal
cd examples/examplewithskm
./gradlew -PskmVersion=1.2.0 clean build
```

Alternatively, configure GitHub Packages credentials with `read:packages`; see [the SKM API guide](https://github.com/VCyte/skm/blob/main/docs/API.md). The generated JAR name remains `examplewithskm.jar` for every SKM version.

## Install and try

1. Install the matching SKM server plugin and `examplewithskm.jar` in Paper's `plugins/` folder.
2. Ensure players have the compatible Fabric SKM client mod.
3. Start the server and run `/examplewithskm add` as an operator or from the console.
4. Connect with an SKM client and press `J`; the test handler replies only to that player.
5. Run `/examplewithskm remove` to remove the binding. Disabling the example plugin also cleans it up.

The v1.1.0-compatible sample is kept separately in `../examplewithskm-v1.1.0`. That version predates runtime registration, so its action is configured statically in SKM's `actions.yml`.
