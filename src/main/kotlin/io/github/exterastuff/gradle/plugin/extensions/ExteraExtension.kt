package io.github.exterastuff.gradle.plugin.extensions

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

abstract class ExteraExtension {
    @get:Nested
    abstract val telegram: TelegramExtension

    @get:Nested
    abstract val shadow: ShadowExtension

    @get:Nested
    abstract val r8: R8Extension

    @get:Nested
    abstract val bundle: BundleExtension

    internal abstract val bundleConfigured: Property<Boolean>

    /**
     * Directory the dex is written to.
     */
    abstract val dexOutputDir: DirectoryProperty

    /**
     * Directory the plugin jar is written to.
     */
    abstract val jarOutputDir: DirectoryProperty

    init {
        bundleConfigured.convention(false)
    }

    fun telegram(action: Action<in TelegramExtension>) =
        action.execute(telegram)

    fun shadow(action: Action<in ShadowExtension>) =
        action.execute(shadow)

    fun r8(action: Action<in R8Extension>) =
        action.execute(r8)

    fun bundle(action: Action<in BundleExtension>) {
        bundleConfigured.set(true)
        action.execute(bundle)
    }
}