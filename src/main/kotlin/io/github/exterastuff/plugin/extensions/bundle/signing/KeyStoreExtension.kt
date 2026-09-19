package io.github.exterastuff.plugin.extensions.bundle.signing

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal

public abstract class KeyStoreExtension {
    @get:InputFile public abstract val path: RegularFileProperty

    @get:Input public abstract val alias: Property<String>

    @get:Internal public abstract val storePassword: Property<String>

    @get:Internal public abstract val keyPassword: Property<String>
}
