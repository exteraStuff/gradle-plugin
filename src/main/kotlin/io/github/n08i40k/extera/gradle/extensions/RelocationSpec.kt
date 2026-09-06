package io.github.n08i40k.extera.gradle.extensions

import org.gradle.api.provider.ListProperty
import javax.inject.Inject

abstract class RelocationSpec @Inject constructor(val pkg: String) {
    abstract val excludes: ListProperty<String>

    fun exclude(vararg patterns: String) {
        excludes.addAll(*patterns)
    }
}