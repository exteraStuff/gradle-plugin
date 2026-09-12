package io.github.exterastuff.gradle.plugin.extensions.bundle.signing

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

abstract class SigningVariantExtension {
    @get:Nested abstract val keyStore: KeyStoreExtension

    @get:Input abstract val tsaUrls: ListProperty<String>

    init {
        tsaUrls.convention(listOf())
    }

    fun keyStore(action: Action<in KeyStoreExtension>) = action.execute(keyStore)
}
