package io.github.exterastuff.plugin.extensions

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

public abstract class ExteraExtension {
    @get:Nested public abstract val telegram: TelegramExtension

    @get:Nested public abstract val shadow: ShadowExtension

    @get:Nested public abstract val r8: R8Extension

    @get:Nested public abstract val bundle: BundleExtension

    internal abstract val bundleConfigured: Property<Boolean>

    /** Directory the dex is written to. */
    public abstract val dexOutputDir: DirectoryProperty

    /** Directory the plugin jar is written to. */
    public abstract val jarOutputDir: DirectoryProperty

    init {
        bundleConfigured.convention(false)
    }

    public fun telegram(action: Action<in TelegramExtension>): Unit = action.execute(telegram)

    public fun shadow(action: Action<in ShadowExtension>): Unit = action.execute(shadow)

    public fun r8(action: Action<in R8Extension>): Unit = action.execute(r8)

    public fun bundle(action: Action<in BundleExtension>) {
        bundleConfigured.set(true)
        action.execute(bundle)
    }
}
