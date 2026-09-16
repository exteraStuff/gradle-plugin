# extera-plugin

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.x-3DDC84?logo=android&logoColor=white)](https://developer.android.com/build)
[![R8](https://img.shields.io/badge/R8-9.4.17-blue)](https://r8.googlesource.com/r8)

A Gradle plugin for building exteraGram plugins written in Kotlin. It takes a regular
Android library module, relocates its dependencies into their own packages, runs
everything through R8 and produces `classes.dex`. If a `bundle` block is present, the
dex is additionally wrapped into a jar whose manifest carries the plugin metadata:
`Plugin-Id`, `Plugin-Class`, version, minimum client version. Service definitions
declared as `providedService` are shipped inside the same jar as separate dexed jars.
The jar can be signed right away with a key from a keystore.

## Setup

The plugin is not published anywhere.
Clone the repository and include it as a composite build.

```sh
git clone https://github.com/exteraStuff/gradle-plugin
```

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("/path/to/gradle-plugin")

    // ...
}
```

```kotlin
// build.gradle.kts
plugins {
    id("com.android.library") version "9.0.1"
    id("io.github.exterastuff.gradle.plugin")
}
```

Requirements:

- **JDK 21** — used to build the plugin itself.
- **Android SDK** — path set in `local.properties`.
- **`com.android.library` in the same module.** The plugin expects AGP and creates
  no tasks without it.
- **`Telegram.jar`** — client classes extracted from the APK with `dex2jar`.

## Configuration

```kotlin
extera {
    telegram {
        jar = file("libs/Telegram.jar")
    }

    shadow {
        targetPackage = "com.example.myplugin_shaded"

        relocate("kotlin", "de.comahe.i18n4k")

        relocate("androidx") {
            // Classes provided by the host (compileOnly) must stay where they are,
            // otherwise the plugin won't find them at runtime.
            exclude("androidx.recyclerview.**")
            exclude("androidx.lifecycle.**")
        }
    }

    r8 {
        proguardFiles.from("proguard-rules.pro")
        minSdk = 26
    }

    // optional
    bundle {
        manifest {
            id = "my-plugin"
            name = "My Plugin"
            description = ":)"
            icon = "someIconPack/10"
            author = "@username"
            version = "1.0.0"

            minClientVersion = "12.1.1"
            entryClass = "com.example.myplugin.Plugin"
        }

        // optional
        signing {
            release {
                keyStore {
                    path = file("keystore.p12")
                    alias = "my-plugin"
                    storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                }
            }
        }
    }
}
```

### `telegram`

| Property              | Default                                                   | Description                                   |
|-----------------------|-----------------------------------------------------------|-----------------------------------------------|
| `jar`                 | —                                                         | Client classes from `dex2jar`                 |
| `conflictingPackages` | `kotlin/`, `kotlinx/coroutines/`, `com/android/tools/r8/` | Package prefixes stripped from `Telegram.jar` |

### `shadow`

| Property        | Default | Description                                   |
|-----------------|---------|-----------------------------------------------|
| `targetPackage` | —       | Package that all relocated classes move under |

`relocate` has two forms: `relocate("kotlin", ...)` moves the listed packages
entirely, while `relocate("androidx") { exclude("androidx.lifecycle.**") }`
leaves some of the classes where they were.

### `r8`

| Property        | Default          | Description                      |
|-----------------|------------------|----------------------------------|
| `proguardFiles` | —                | R8 rules                         |
| `version`       | `9.4.17`         | R8 version used to build the dex |
| `minSdk`        | variant `minSdk` | Minimum API level for the dex    |

### `bundle`

This block is optional. Without it the plugin only builds the dex, and the
`packagePluginJar*` tasks are not created.

#### `manifest`

| Property           | Default | Description                                              |
|--------------------|---------|----------------------------------------------------------|
| `id`               | —       | Jar file name and `Plugin-Id` in the manifest            |
| `name`             | —       | Display name                                             |
| `description`      | —       | Description                                              |
| `icon`             | —       | Plugin emoji icon                                        |
| `author`           | —       | Author                                                   |
| `version`          | —       | Plugin version, also used in the jar name                |
| `minClientVersion` | —       | Minimum exteraGram version, e.g. `"12.1.1"`              |
| `entryClass`       | —       | Fully qualified name of the class the client starts from |
| `updateSources`    | empty   | Update sources: name → url                               |
| `dependencies`     | empty   | Other plugins this one requires: id → version            |

#### `signing`

Contains two blocks, `debug` and `release`, one per build variant. The
`signPluginJar*` task is created only for variants whose block is defined.

| Property   | Default                                                           | Description                       |
|------------|-------------------------------------------------------------------|-----------------------------------|
| `keyStore` | —                                                                 | Key used to sign the jar          |
| `tsaUrls`  | `debug` — empty; `release` — DigiCert, Sectigo, `rfc3161.ai.moda` | Timestamp servers, tried in order |

If `tsaUrls` is empty, the jar is signed without a timestamp. If the list is set but
none of the servers respond, the task fails.

`keyStore` fields:

| Property        | Default         | Description                   |
|-----------------|-----------------|-------------------------------|
| `path`          | —               | Keystore file                 |
| `alias`         | —               | Key alias inside the keystore |
| `storePassword` | —               | Keystore password             |
| `keyPassword`   | `storePassword` | Key password, if different    |

### Output directories

| Property       | Default             | Description              |
|----------------|---------------------|--------------------------|
| `dexOutputDir` | `build/outputs/dex` | Where the dex is written |
| `jarOutputDir` | `build/outputs/jar` | Where the jar is written |

## Services

A plugin can expose service definitions to other plugins — interfaces and types
through which it is accessed from the outside. Such a definition is not merged into
the plugin's dex; it is shipped inside the plugin jar as a separate dexed jar, so the
client can load it once and share it with everyone who requires it.

### `providedService`

The `providedService` configuration in the `dependencies` block declares a service
definition that the plugin provides.

```kotlin
dependencies {
    providedService(project(":api"))
}
```

Such a dependency:

- is added to `compileOnly` — the plugin compiles against it, shadow does not pull it
  into the fat jar, and R8 sees it only on the classpath;
- is resolved without transitives: the artifact is treated as self-contained;
- is run through D8 and placed at `services/<group>.<artifact>-<version>.jar` inside
  the plugin jar. The nested jar contains `classes.dex` and the resources of the
  original artifact, and its manifest carries `Service-Id` and `Service-Version`;
- is listed in the plugin manifest under `Plugin-Provided-Services`.

Service classes are not relocated: their names stay the same as those seen by the
compiled plugin code — otherwise callers would not be able to find them.

Coordinates are taken from the resolution result, so `project(":api")` appears in the
manifest as the subproject's `group:name:version`, not as the `:api` path.

### `requiredService`

The same kind of configuration, but the service definition is not shipped in the
plugin jar — it is only declared as a requirement that must be provided by the client
or by another installed plugin.

```kotlin
dependencies {
    requiredService("io.github.exterastuff.other:api:1.2.0")
}
```

It behaves the same way — `compileOnly`, no transitives, coordinates from resolution —
but it is not run through D8 and does not end up in `services/`. It is listed under
`Plugin-Required-Services`.

### Manifest attributes

Both lists are sorted and comma-separated:

```
Plugin-Provided-Services: io.github.exterastuff.demo:api:2.4.1
Plugin-Required-Services: io.github.exterastuff.other:api:1.2.0
```

The attributes are always written; an empty value means there are no such
dependencies.

## Building

```sh
./gradlew buildDexDebug        # build/outputs/dex/debug/classes.dex
./gradlew buildDexRelease      # build/outputs/dex/release/classes.dex
./gradlew buildDex             # both variants
```

With a `bundle` block, jar packaging is also available:

```sh
./gradlew packagePluginJarRelease   # build/outputs/jar/<id>-<version>.jar
./gradlew packagePluginJarDebug     # build/outputs/jar/<id>-<version>-debug.jar
./gradlew packagePluginJar          # both variants
```

And with `signing` defined, the built jar can be signed:

```sh
./gradlew signPluginJarRelease      # build/outputs/jar/<id>-<version>-signed.jar
./gradlew signPluginJarDebug        # build/outputs/jar/<id>-<version>-debug-signed.jar
./gradlew signPluginJar             # all defined variants
```

The debug variant is built by R8 in `DEBUG` mode: some optimizations are disabled,
the build is faster and stack traces are more readable.

## Supported Gradle features

| Feature             | Support                                                                                                                                                       |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Configuration cache | Supported                                                                                                                                                     |
| Build cache         | `processTelegramJar`, `shade*` and `dexProvidedServices*` are cacheable; `buildDex*`, `packagePluginJar*` and `signPluginJar*` rely on up-to-date checks only |
| Parallel execution  | Supported                                                                                                                                                     |

Checked on Gradle 9.7.1 with AGP 9.x.

## Build pipeline

1. **Cleaning `Telegram.jar`.** ASM restores the `InnerClasses` attributes broken by
   `dex2jar` and strips the packages listed in `conflictingPackages`, so client
   classes don't clash with the project's dependencies.
2. **Fat jar.** Compiled classes and runtime dependencies are merged into a single jar
   with shadow; packages from `relocate` are moved under `targetPackage` — otherwise
   they would clash with identical classes inside the client itself.
3. **R8.** The fat jar is the input, the cleaned `Telegram.jar` and `compileOnly`
   dependencies go on the classpath, `android.jar` goes into the library. The output
   is a dex.
4. **Provided services.** Each one is run through D8 separately and turned into a jar
   containing `classes.dex` instead of classes.
5. **Packaging.** The dex is put into the jar, the dexed jars go into the `services/`
   directory, and the plugin metadata is written to the manifest.
6. **Signing.** The jar is signed with a key from the keystore using `SHA256withRSA`;
   the timestamp is taken from the first TSA in the list that responds.
