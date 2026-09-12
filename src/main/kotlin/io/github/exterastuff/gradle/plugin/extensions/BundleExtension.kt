package io.github.exterastuff.gradle.plugin.extensions

import io.github.exterastuff.gradle.plugin.extensions.bundle.ManifestExtension
import io.github.exterastuff.gradle.plugin.extensions.bundle.SigningExtension
import org.gradle.api.Action
import org.gradle.api.tasks.Nested

abstract class BundleExtension {
    @get:Nested abstract val manifest: ManifestExtension

    @get:Nested abstract val signing: SigningExtension

    fun manifest(action: Action<in ManifestExtension>) = action.execute(manifest)

    fun signing(action: Action<in SigningExtension>) = action.execute(signing)
}
