package io.github.exterastuff.gradle.plugin.extensions.bundle.signing

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal

abstract class KeyStoreExtension {
    @get:InputFile
    abstract val path: RegularFileProperty

    @get:Input
    abstract val alias: Property<String>

    @get:Internal
    abstract val storePassword: Property<String>

    @get:Internal
    abstract val keyPassword: Property<String>
}
