package io.github.exterastuff.gradle.plugin.extensions

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class ShadowExtension @Inject constructor(private val objects: ObjectFactory) {
    abstract class RelocationSpec @Inject constructor(val pkg: String) {
        abstract val excludes: ListProperty<String>

        fun exclude(vararg patterns: String) {
            excludes.addAll(*patterns)
        }
    }

    /**
     * Parent package every relocated package is moved under.
     *
     * Example: `"io.github.n08i40k.extera_shaded"`
     */
    abstract val targetPackage: Property<String>

    abstract val relocations: ListProperty<RelocationSpec>

    private fun newSpec(pkg: String): RelocationSpec =
        objects.newInstance(RelocationSpec::class.java, pkg.trimEnd('.'))

    /** Relocates (shades) provided package name to `shadedPackage`. */
    fun relocate(vararg packages: String) {
        packages.forEach { relocations.add(newSpec(it)) }
    }

    /**
     * Relocates (shades) provided package name to `shadedPackage`. Also, can exclude certain
     * sub-packages or classes in the provided package from relocation.
     */
    fun relocate(pkg: String, action: Action<RelocationSpec>) {
        relocations.add(newSpec(pkg).apply { action.execute(this) })
    }
}
