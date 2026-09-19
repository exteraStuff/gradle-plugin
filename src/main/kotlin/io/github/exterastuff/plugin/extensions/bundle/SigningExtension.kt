package io.github.exterastuff.plugin.extensions.bundle

import io.github.exterastuff.plugin.extensions.bundle.signing.SigningVariantExtension
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

public abstract class SigningExtension {
    @get:Nested public abstract val debug: SigningVariantExtension

    internal abstract val debugConfigured: Property<Boolean>

    @get:Nested public abstract val release: SigningVariantExtension

    internal abstract val releaseConfigured: Property<Boolean>

    init {
        debugConfigured.convention(false)
        releaseConfigured.convention(false)

        release.tsaUrls.convention(
            listOf(
                "http://timestamp.digicert.com",
                "http://timestamp.sectigo.com",
                "http://rfc3161.ai.moda",
            )
        )
    }

    public fun debug(action: Action<in SigningVariantExtension>) {
        debugConfigured.set(true)
        action.execute(debug)
    }

    public fun release(action: Action<in SigningVariantExtension>) {
        releaseConfigured.set(true)
        action.execute(release)
    }
}
