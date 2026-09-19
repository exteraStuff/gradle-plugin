package io.github.exterastuff.plugin.extensions

import io.github.exterastuff.plugin.extensions.bundle.ManifestExtension
import io.github.exterastuff.plugin.extensions.bundle.SigningExtension
import org.gradle.api.Action
import org.gradle.api.tasks.Nested

public abstract class BundleExtension {
    @get:Nested public abstract val manifest: ManifestExtension

    @get:Nested public abstract val signing: SigningExtension

    public fun manifest(action: Action<in ManifestExtension>): Unit = action.execute(manifest)

    public fun signing(action: Action<in SigningExtension>): Unit = action.execute(signing)
}
