package io.github.exterastuff.gradle.plugin.extensions

import org.gradle.api.provider.Property

abstract class PluginManifestExtension {
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
}