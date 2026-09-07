# extera-plugin

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.x-3DDC84?logo=android&logoColor=white)](https://developer.android.com/build)
[![R8](https://img.shields.io/badge/R8-9.4.17-blue)](https://r8.googlesource.com/r8)

Gradle-плагин для сборки плагинов exteraGram, написанных на Kotlin. Берёт обычный
Android-library модуль, уводит зависимости в свои пакеты, прогоняет всё через R8 и
отдаёт `classes.dex`. Если задан блок `manifest`, dex дополнительно заворачивается
в jar, в манифесте которого лежат метаданные плагина: `Plugin-Id`, `Plugin-Class`,
версия, минимальная версия клиента.

## Подключение

Плагин нигде не опубликован.
Репозиторий нужно склонировать и подключить как composite build.

```sh
git clone https://github.com/n08i40k/extera-gradle-plugin
```

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("/path/to/extera-gradle-plugin")

    // ...
}
```

```kotlin
// build.gradle.kts
plugins {
    id("com.android.library") version "9.0.1"
    id("io.github.n08i40k.extera")
}
```

Что должно быть на месте:

- **JDK 21** — на нём собирается сам плагин.
- **Android SDK** — путь в `local.properties`.
- **`com.android.library` в том же модуле.** Плагин ждёт AGP и без него не создаёт
  ни одной задачи.
- **`Telegram.jar`** — классы клиента, вытащенные из APK через `dex2jar`.

## Настройка

```kotlin
extera {
    telegram {
        jar = file("libs/Telegram.jar")
    }

    shadow {
        targetPackage = "com.example.myplugin_shaded"

        relocate("kotlin", "de.comahe.i18n4k")

        relocate("androidx") {
            // Классы, которые приходят из хоста (compileOnly), должны остаться
            // на своих местах — иначе плагин не найдёт их в рантайме.
            exclude("androidx.recyclerview.**")
            exclude("androidx.lifecycle.**")
        }
    }

    r8 {
        proguardFiles.from("proguard-rules.pro")
        minSdk = 26
    }

    manifest {
        id = "my-plugin"
        name = "My Plugin"
        description = ":)"
        author = "@username"
        version = "1.0.0"

        minClientVersion = "12.1.1"
        entryClass = "com.example.myplugin.Plugin"
    }
}
```

### `telegram`

| Свойство              | По умолчанию                                              | Что делает                                               |
|-----------------------|-----------------------------------------------------------|----------------------------------------------------------|
| `jar`                 | —                                                         | Классы клиента из `dex2jar`                              |
| `conflictingPackages` | `kotlin/`, `kotlinx/coroutines/`, `com/android/tools/r8/` | Префиксы пакетов, которые выкидываются из `Telegram.jar` |

### `shadow`

| Свойство        | По умолчанию | Что делает                                   |
|-----------------|--------------|----------------------------------------------|
| `targetPackage` | —            | Пакет, под который уезжает всё релоцированное |

У `relocate` две формы: `relocate("kotlin", ...)` переносит
перечисленные пакеты целиком, а `relocate("androidx") { exclude("androidx.lifecycle.**") }`
оставляет часть классов там, где они были.

### `r8`

| Свойство        | По умолчанию      | Что делает                          |
|-----------------|-------------------|-------------------------------------|
| `proguardFiles` | —                 | Правила для R8                      |
| `version`       | `9.4.17`          | Версия R8, которой собирается dex   |
| `minSdk`        | `minSdk` варианта | Минимальный API level для dex       |

### `manifest`

Блок необязателен. Без него плагин собирает только dex, а задачи
`packagePluginJar*` не создаются.

| Свойство           | По умолчанию | Что делает                                            |
|--------------------|--------------|-------------------------------------------------------|
| `id`               | —            | Имя jar-файла и `Plugin-Id` в манифесте               |
| `name`             | —            | Отображаемое имя                                      |
| `description`      | —            | Описание                                              |
| `author`           | —            | Автор                                                 |
| `version`          | —            | Версия плагина, попадает и в имя jar                  |
| `minClientVersion` | —            | Минимальная версия exteraGram, например `"12.1.1"`    |
| `entryClass`       | —            | Полное имя класса, с которого клиент запускает плагин |

### Выходные каталоги

| Свойство       | По умолчанию         | Что делает              |
|----------------|----------------------|-------------------------|
| `dexOutputDir` | `build/outputs/dex`  | Куда складывать dex     |
| `jarOutputDir` | `build/outputs/jar`  | Куда складывать jar     |

## Сборка

```sh
./gradlew buildDexDebug        # build/outputs/dex/debug/classes.dex
./gradlew buildDexRelease      # build/outputs/dex/release/classes.dex
./gradlew buildDex             # оба варианта
```

С заданным блоком `manifest` доступна ещё и упаковка в jar:

```sh
./gradlew packagePluginJarRelease   # build/outputs/jar/<id>-<version>.jar
./gradlew packagePluginJarDebug     # build/outputs/jar/<id>-<version>-debug.jar
./gradlew packagePluginJar          # оба варианта
```

Debug-вариант R8 собирает в режиме `DEBUG`: часть оптимизаций отключена, сборка
быстрее, стектрейсы читаемее.

## Этапы сборки

1. **Чистка `Telegram.jar`.** ASM восстанавливает атрибуты `InnerClasses`, которые
   ломает `dex2jar`, и выбрасывает пакеты из `conflictingPackages`, чтобы классы
   клиента не спорили с зависимостями проекта.
2. **Fat jar.** Скомпилированные классы и runtime-зависимости собираются в один jar
   через shadow, пакеты из `relocate` уезжают под `targetPackage` — иначе они
   столкнулись бы с такими же классами внутри самого клиента.
3. **R8.** Fat jar идёт на вход, почищенный `Telegram.jar` и `compileOnly`-зависимости — в
   classpath, `android.jar` — в library. На выходе dex.
4. **Упаковка.** Dex кладётся в jar, метаданные плагина пишутся в манифест.
