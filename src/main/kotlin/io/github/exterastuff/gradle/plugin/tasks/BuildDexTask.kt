package io.github.exterastuff.gradle.plugin.tasks

import io.github.exterastuff.gradle.plugin.actions.R8WorkAction
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

abstract class BuildDexTask : DefaultTask() {
    /** Plugin's fat-jar. */
    @get:Classpath abstract val programJars: ConfigurableFileCollection

    /** Libraries that available on every device. */
    @get:Classpath abstract val bootClasspathJars: ConfigurableFileCollection

    /** Libraries that available in target application (exteraGram). */
    @get:Classpath abstract val classpathJars: ConfigurableFileCollection

    /** R8 and its dependencies. Will be loaded into an isolated classloader. */
    @get:Classpath abstract val r8Classpath: ConfigurableFileCollection

    /** ProGuard rules. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val proguardFiles: ConfigurableFileCollection

    /** Packages whose original names are missing after relocation. */
    @get:Input abstract val relocatedPackages: ListProperty<String>

    /** Minimal SDK version. Same as minSdk at compile time. */
    @get:Input abstract val minSdk: Property<Int>

    /** Disable some optimizations in debug builds. */
    @get:Input abstract val release: Property<Boolean>

    /** Output file that contains merged classpath to avoid collisions at r8 step. */
    @get:LocalState abstract val mergedClasspathJar: RegularFileProperty

    /** Path to output dex. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val workers: WorkerExecutor

    @TaskAction
    fun run() {
        val queue = workers.classLoaderIsolation { classpath.from(r8Classpath) }

        queue.submit(R8WorkAction::class.java) {
            programJars.from(this@BuildDexTask.programJars)
            bootClasspathJars.from(this@BuildDexTask.bootClasspathJars)
            classpathJars.from(this@BuildDexTask.classpathJars)
            proguardFiles.from(this@BuildDexTask.proguardFiles)
            relocatedPackages.set(this@BuildDexTask.relocatedPackages)
            minSdk.set(this@BuildDexTask.minSdk)
            release.set(this@BuildDexTask.release)
            mergedClasspathJar.set(this@BuildDexTask.mergedClasspathJar)
            outputDir.set(this@BuildDexTask.outputDir)
        }

        queue.await()

        logger.lifecycle("dex written to ${outputDir.get().asFile}")
    }
}
