package io.github.exterastuff.plugin.extensions.bundle

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class ManifestExtension @Inject constructor(private val objects: ObjectFactory) {
    /**
     * Another plugin this one needs. Only a major bump of the dependency counts as
     * incompatible.
     */
    abstract class DependencySpec(val id: String) {
        /**
         * Minimal supported version of the dependency, as `major.minor.patch`.
         *
         * Example: `"1.2.0"`
         */
        abstract val minVersion: Property<String>

        /** Places the client can download the dependency from. */
        abstract val providerSources: ListProperty<String>

        /** SHA-1 of the jar served by `providerSources`. */
        abstract val providerSha1: Property<String>

        /** Adds a place the client can download the dependency from. */
        fun providerSource(url: String) {
            providerSources.add(url)
        }
    }

    /** Plugin metadata. */
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

    /** Other plugins this one needs, declared with [dependency]. */
    abstract val dependencies: ListProperty<DependencySpec>

    private fun newDependency(id: String): DependencySpec =
        objects.newInstance(DependencySpec::class.java, id)

    /**
     * Declares a plugin this one needs.
     *
     * Example: `dependency("streaks") { minVersion = "1.2.0" }`
     */
    fun dependency(id: String, action: Action<in DependencySpec>) {
        dependencies.add(newDependency(id).apply(action::execute))
    }
}
