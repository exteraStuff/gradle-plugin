package io.github.exterastuff.gradle.plugin.extensions

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

abstract class R8Extension {
    private companion object {
        const val DEFAULT_R8_VERSION = "9.4.17"
    }

    /** List of files with proguard rules. */
    abstract val proguardFiles: ConfigurableFileCollection

    /**
     * Version of r8 that will be used to convert .jar to .dex and shrink unused code.
     *
     * Example: `"9.4.17"`
     */
    abstract val version: Property<String>

    /**
     * Minimal Android SDK version. Should be same as minSdk in `android` block.
     *
     * Example: `26`
     */
    abstract val minSdk: Property<Int>

    init {
        version.convention(DEFAULT_R8_VERSION)
    }
}
