package io.github.exterastuff.gradle.plugin.extensions.bundle

import io.github.exterastuff.gradle.plugin.extensions.bundle.signing.SigningVariantExtension
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

abstract class SigningExtension {
    @get:Nested
    abstract val debug: SigningVariantExtension

    internal abstract val debugConfigured: Property<Boolean>

    @get:Nested
    abstract val release: SigningVariantExtension

    internal abstract val releaseConfigured: Property<Boolean>

    init {
        debugConfigured.convention(false)
        releaseConfigured.convention(false)

        release.tsaUrls.convention(
            listOf(
                "http://timestamp.digicert.com",
                "http://timestamp.sectigo.com",
                "http://rfc3161.ai.moda"
            )
        )
    }

    fun debug(action: Action<in SigningVariantExtension>) {
        debugConfigured.set(true)
        action.execute(debug)
    }

    fun release(action: Action<in SigningVariantExtension>) {
        releaseConfigured.set(true)
        action.execute(release)
    }
}