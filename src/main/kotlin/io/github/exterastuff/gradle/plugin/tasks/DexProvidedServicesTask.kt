package io.github.exterastuff.gradle.plugin.tasks

import io.github.exterastuff.gradle.plugin.actions.D8WorkAction
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor

@CacheableTask
abstract class DexProvidedServicesTask : DefaultTask() {
    companion object {
        /** Name a service takes inside the plugin jar, without the extension. */
        internal fun serviceBaseName(coordinates: String): String {
            val (group, artifact, version) = coordinates.split(':')
            return "$group.$artifact-$version"
        }
    }

    /** A single `providedService` dependency. */
    abstract class ProvidedServiceSpec {
        /** Maven coordinates of the service, `group:artifact:version`. */
        @get:Input abstract val coordinates: Property<String>

        /** Jar of the service, as it comes from the resolved classpath. */
        @get:Classpath abstract val jar: RegularFileProperty
    }

    @get:Nested abstract val providedServices: ListProperty<ProvidedServiceSpec>

    /** Libraries that available on every device. */
    @get:Classpath abstract val bootClasspathJars: ConfigurableFileCollection

    /** Libraries the services compile against, needed to desugar them. */
    @get:Classpath abstract val classpathJars: ConfigurableFileCollection

    /** R8 and its dependencies. Will be loaded into an isolated classloader. */
    @get:Classpath abstract val r8Classpath: ConfigurableFileCollection

    /** Minimal SDK version. Same as minSdk at compile time. */
    @get:Input abstract val minSdk: Property<Int>

    /** Disable some optimizations in debug builds. */
    @get:Input abstract val release: Property<Boolean>

    /** Directory the raw dex files are written to before packing. */
    @get:LocalState abstract val workDir: DirectoryProperty

    /** Directory holding one dexed jar per service. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val workers: WorkerExecutor

    @TaskAction
    fun run() {
        val output =
            outputDir.get().asFile.apply {
                deleteRecursively()
                mkdirs()
            }

        val specs = providedServices.get()

        if (specs.isEmpty()) {
            logger.info("no providedService dependencies declared")
            return
        }

        val work = workDir.get().asFile
        val queue = workers.classLoaderIsolation { classpath.from(r8Classpath) }

        for (spec in specs) {
            val specCoordinates = spec.coordinates.get()
            val baseName = serviceBaseName(specCoordinates)

            queue.submit(D8WorkAction::class.java) {
                coordinates.set(specCoordinates)
                inputJar.set(spec.jar)
                bootClasspathJars.from(this@DexProvidedServicesTask.bootClasspathJars)
                classpathJars.from(this@DexProvidedServicesTask.classpathJars)
                minSdk.set(this@DexProvidedServicesTask.minSdk)
                release.set(this@DexProvidedServicesTask.release)
                workDir.set(work.resolve(baseName))
                outputJar.set(output.resolve("$baseName.jar"))
            }
        }

        queue.await()
    }
}
