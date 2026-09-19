package io.github.exterastuff.plugin.extensions.bundle.signing

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

public abstract class SigningVariantExtension {
    @get:Nested public abstract val keyStore: KeyStoreExtension

    @get:Input public abstract val tsaUrls: ListProperty<String>

    init {
        tsaUrls.convention(listOf())
    }

    public fun keyStore(action: Action<in KeyStoreExtension>): Unit = action.execute(keyStore)
}
