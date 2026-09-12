# extera-plugin

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.x-3DDC84?logo=android&logoColor=white)](https://developer.android.com/build)
[![R8](https://img.shields.io/badge/R8-9.4.17-blue)](https://r8.googlesource.com/r8)

Gradle-плагин для сборки плагинов exteraGram, написанных на Kotlin. Берёт обычный
Android-library модуль, уводит зависимости в свои пакеты, прогоняет всё через R8 и
отдаёт `classes.dex`. Если задан блок `bundle`, dex дополнительно заворачивается
в jar, в манифесте которого лежат метаданные плагина: `Plugin-Id`, `Plugin-Class`,
версия, минимальная версия клиента. Библиотеки, объявленные как `fatJar`, едут в том
же jar отдельными dexed-jar'ами. Jar можно сразу подписать ключом из keystore.

## Подключение

Плагин нигде не опубликован.
Репозиторий нужно склонировать и подключить как composite build.

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

| Свойство              | По умолчанию                                              | Что делает                                               |
|-----------------------|-----------------------------------------------------------|----------------------------------------------------------|
| `jar`                 | —                                                         | Классы клиента из `dex2jar`                              |
| `conflictingPackages` | `kotlin/`, `kotlinx/coroutines/`, `com/android/tools/r8/` | Префиксы пакетов, которые выкидываются из `Telegram.jar` |

### `shadow`

| Свойство        | По умолчанию | Что делает                                    |
|-----------------|--------------|-----------------------------------------------|
| `targetPackage` | —            | Пакет, под который уезжает всё релоцированное |

У `relocate` две формы: `relocate("kotlin", ...)` переносит
перечисленные пакеты целиком, а `relocate("androidx") { exclude("androidx.lifecycle.**") }`
оставляет часть классов там, где они были.

### `r8`

| Свойство        | По умолчанию      | Что делает                        |
|-----------------|-------------------|-----------------------------------|
| `proguardFiles` | —                 | Правила для R8                    |
| `version`       | `9.4.17`          | Версия R8, которой собирается dex |
| `minSdk`        | `minSdk` варианта | Минимальный API level для dex     |

### `bundle`

Блок необязателен. Без него плагин собирает только dex, а задачи
`packagePluginJar*` не создаются.

#### `manifest`

| Свойство           | По умолчанию | Что делает                                            |
|--------------------|--------------|-------------------------------------------------------|
| `id`               | —            | Имя jar-файла и `Plugin-Id` в манифесте               |
| `name`             | —            | Отображаемое имя                                      |
| `description`      | —            | Описание                                              |
| `icon`             | —            | Эмодзи-иконка плагина                                 |
| `author`           | —            | Автор                                                 |
| `version`          | —            | Версия плагина, попадает и в имя jar                  |
| `minClientVersion` | —            | Минимальная версия exteraGram, например `"12.1.1"`    |
| `entryClass`       | —            | Полное имя класса, с которого клиент запускает плагин |
| `updateSources`    | пусто        | Источники обновлений: имя → url                       |
| `dependencies`     | пусто        | Другие плагины, которые нужны этому: id → версия      |

#### `signing`

Внутри два блока — `debug` и `release`, по одному на вариант сборки. Задача
`signPluginJar*` создаётся только для того варианта, чей блок описан.

| Свойство   | По умолчанию                                                      | Что делает                                       |
|------------|-------------------------------------------------------------------|--------------------------------------------------|
| `keyStore` | —                                                                 | Ключ, которым подписывается jar                  |
| `tsaUrls`  | `debug` — пусто; `release` — DigiCert, Sectigo, `rfc3161.ai.moda` | Серверы штампов времени, перебираются по порядку |

Если `tsaUrls` пуст, jar подписывается без штампа времени. Если список задан, но
ни один сервер не ответил, задача падает.

Поля `keyStore`:

| Свойство        | По умолчанию    | Что делает                    |
|-----------------|-----------------|-------------------------------|
| `path`          | —               | Файл keystore                 |
| `alias`         | —               | Алиас ключа внутри keystore   |
| `storePassword` | —               | Пароль keystore               |
| `keyPassword`   | `storePassword` | Пароль ключа, если отличается |

### Выходные каталоги

| Свойство       | По умолчанию        | Что делает          |
|----------------|---------------------|---------------------|
| `dexOutputDir` | `build/outputs/dex` | Куда складывать dex |
| `jarOutputDir` | `build/outputs/jar` | Куда складывать jar |

## fatJar-зависимости

Конфигурация `fatJar` в блоке `dependencies` объявляет библиотеку, которая едет
внутри jar плагина отдельным dexed-jar, а не растворяется в dex самого плагина.

```kotlin
dependencies {
    fatJar("org.jsoup:jsoup:1.18.3")
    fatJar(project(":api"))
}
```

Такая зависимость:

- добавляется в `compileOnly` — плагин компилируется против неё, shadow не тянет её
  в fat jar, а R8 видит её только в classpath;
- резолвится без транзитивов: артефакт считается самодостаточным;
- прогоняется через D8 и ложится в `fatjars/<group>.<artifact>-<version>.jar` внутри
  jar плагина. Внутри вложенного jar лежат `classes.dex` и ресурсы исходного
  артефакта, а его манифест несёт `Fat-Jar-Id` и `Fat-Jar-Version`;
- перечисляется в манифесте плагина в `Plugin-Fat-Jars`.

Классы `fatJar`-зависимости не релоцируются: их имена остаются теми же, что видит
скомпилированный код плагина.

Координаты берутся из результата резолва, поэтому `project(":api")` попадает в
манифест как `group:name:version` подпроекта, а не как путь `:api`.

### `requiredFatJar`

Та же конфигурация, но библиотека едет не в jar плагина, а лишь объявляется как
требование — её должен предоставить клиент или другой уже установленный плагин.

```kotlin
dependencies {
    requiredFatJar("com.squareup.okio:okio-jvm:3.9.1")
}
```

Ведёт себя так же — `compileOnly`, без транзитивов, координаты из резолва, — но
через D8 не проходит и в `fatjars/` не попадает. Перечисляется в
`Plugin-Required-Fat-Jars`.

### Атрибуты манифеста

Оба списка отсортированы и разделены запятой:

```
Plugin-Fat-Jars: io.github.exterastuff.demo:api:2.4.1, org.jsoup:jsoup:1.18.3
Plugin-Required-Fat-Jars: com.squareup.okio:okio-jvm:3.9.1
```

Атрибуты пишутся всегда; пустое значение означает, что таких зависимостей нет.

## Сборка

```sh
./gradlew buildDexDebug        # build/outputs/dex/debug/classes.dex
./gradlew buildDexRelease      # build/outputs/dex/release/classes.dex
./gradlew buildDex             # оба варианта
```

С заданным блоком `bundle` доступна ещё и упаковка в jar:

```sh
./gradlew packagePluginJarRelease   # build/outputs/jar/<id>-<version>.jar
./gradlew packagePluginJarDebug     # build/outputs/jar/<id>-<version>-debug.jar
./gradlew packagePluginJar          # оба варианта
```

А с описанным `signing` — подпись собранного jar:

```sh
./gradlew signPluginJarRelease      # build/outputs/jar/<id>-<version>-signed.jar
./gradlew signPluginJarDebug        # build/outputs/jar/<id>-<version>-debug-signed.jar
./gradlew signPluginJar             # все описанные варианты
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
4. **fatJar-зависимости.** Каждая отдельно проходит через D8 и превращается в jar,
   где вместо классов лежит `classes.dex`.
5. **Упаковка.** Dex кладётся в jar, dexed-jar'ы — в каталог `fatjars/`, метаданные
   плагина пишутся в манифест.
6. **Подпись.** Jar подписывается ключом из keystore алгоритмом `SHA256withRSA`;
   штамп времени берётся у первого ответившего TSA из списка.
