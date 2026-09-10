package io.github.exterastuff.gradle.plugin.extensions.bundle

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class ManifestExtension {
    /**
     * Plugin metadata.
     */
    abstract val id: Property<String>
    abstract val name: Property<String>
    abstract val description: Property<String>
    abstract val icon: Property<String>
    abstract val author: Property<String>
    abstract val version: Property<String>

    /**
     * Minimal supported exteraGram version.
     *
     * Example: `"12.1.1"`
     */
    abstract val minClientVersion: Property<String>

    /**
     * Entry class of plugin.
     *
     * Example: `"ru.n08i40k.streaks.Plugin"`
     */
    abstract val entryClass: Property<String>

    /**
     * Places the client looks for a newer build, as source name to url.
     *
     * Example: `mapOf("github" to "https://github.com/exteraStuff/streaks/releases")`
     */
    abstract val updateSources: MapProperty<String, String>

    /**
     * Other plugins this one needs, as plugin id to the minimal supported version.
     * Only a major bump of the dependency counts as incompatible.
     *
     * Example: `mapOf("streaks" to "1.2.0")`
     */
    abstract val dependencies: MapProperty<String, String>
}