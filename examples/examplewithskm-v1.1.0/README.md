# ExampleWithSKM for SKM 1.1.0

This historical example is built against `tmin.click:skm-api:1.1.0`. SKM 1.1.0 does not have `ActionRegistrationService`, so the example's action must be declared in SKM's `plugins/SKM/actions.yml`. The handler registers as the v1.1 global `ActionExecutionService`, accepts `examplewithskm.ping`, and sends a private test response to the player who pressed the key. It does not broadcast.

Append this entry to `plugins/SKM/actions.yml`:

```yaml
actions:
  examplewithskm.ping:
    name: "ExampleWithSKM Ping"
    default-key: "key.keyboard.j"
    cooldown-ms: 750
    category: "ExampleWithSKM"
    holdable: false
```

Build with Java 25 after installing the v1.1 API to Maven Local, or configure GitHub Packages credentials with `read:packages`:

```bash
# From a checkout of tag v1.1.0:
./gradlew :skm-api:publishToMavenLocal
cd examples/examplewithskm-v1.1.0
./gradlew -PskmVersion=1.1.0 clean build
```

The artifact name is `build/libs/examplewithskm.jar`. Install it with `SKM-Server-1.1.0.jar`; players also need the v4-compatible SKM Fabric client. The v1.2.0 example demonstrates runtime registration and does not need the YAML entry.
