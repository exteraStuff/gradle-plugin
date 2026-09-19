package io.github.exterastuff.plugin.extensions.bundle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

public abstract class ManifestExtension @Inject constructor(private val objects: ObjectFactory) {
    /**
     * Another plugin this one needs. Only a major bump of the dependency counts as incompatible.
     */
    public abstract class DependencySpec(public val id: String) {
        /**
         * Minimal supported version of the dependency, as `major.minor.patch`.
         *
         * Example: `"1.2.0"`
         */
        public abstract val minVersion: Property<String>

        /** Places the client can download the dependency from. */
        public abstract val providerSources: ListProperty<String>

        /** SHA-1 of the jar served by `providerSources`. */
        public abstract val providerSha1: Property<String>

        /** Adds a place the client can download the dependency from. */
        public fun providerSource(url: String) {
            providerSources.add(url)
        }
    }

    /** Plugin metadata. */
    public abstract val id: Property<String>
    public abstract val name: Property<String>
    public abstract val description: Property<String>
    public abstract val icon: Property<String>
    public abstract val author: Property<String>
    public abstract val version: Property<String>

    /**
     * Minimal supported exteraGram version.
     *
     * Example: `"12.1.1"`
     */
    public abstract val minClientVersion: Property<String>

    /**
     * Entry class of plugin.
     *
     * Example: `"ru.n08i40k.streaks.Plugin"`
     */
    public abstract val entryClass: Property<String>

    /**
     * Places the client looks for a newer build, as source name to url.
     *
     * Example: `mapOf("github" to "https://github.com/exteraStuff/streaks/releases")`
     */
    public abstract val updateSources: MapProperty<String, String>

    /** Other plugins this one needs, declared with [dependency]. */
    internal abstract val dependencies: ListProperty<DependencySpec>

    private fun newDependency(id: String): DependencySpec =
        objects.newInstance(DependencySpec::class.java, id)

    /**
     * Declares a plugin this one needs.
     *
     * Example: `dependency("streaks") { minVersion = "1.2.0" }`
     */
    public fun dependency(id: String, action: Action<in DependencySpec>): Unit =
        dependencies.add(newDependency(id).apply(action::execute))
}
