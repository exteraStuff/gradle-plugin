package io.github.exterastuff.plugin.extensions

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

public abstract class ShadowExtension @Inject constructor(private val objects: ObjectFactory) {
    public abstract class RelocationSpec @Inject constructor(public val pkg: String) {
        public abstract val excludes: ListProperty<String>

        public fun exclude(vararg patterns: String) {
            excludes.addAll(*patterns)
        }
    }

    /**
     * Parent package every relocated package is moved under.
     *
     * Example: `"io.github.n08i40k.extera_shaded"`
     */
    public abstract val targetPackage: Property<String>

    internal abstract val relocations: ListProperty<RelocationSpec>

    private fun newSpec(pkg: String): RelocationSpec =
        objects.newInstance(RelocationSpec::class.java, pkg.trimEnd('.'))

    /** Relocates (shades) provided package name to `shadedPackage`. */
    public fun relocate(vararg packages: String): Unit = packages.forEach {
        relocations.add(newSpec(it))
    }

    /**
     * Relocates (shades) provided package name to `shadedPackage`. Also, can exclude certain
     * sub-packages or classes in the provided package from relocation.
     */
    public fun relocate(pkg: String, action: Action<RelocationSpec>): Unit =
        relocations.add(newSpec(pkg).apply(action::execute))
}
