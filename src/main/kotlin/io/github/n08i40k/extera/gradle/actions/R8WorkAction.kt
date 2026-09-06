package io.github.n08i40k.extera.gradle.actions

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.origin.Origin
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val ConfigurableFileCollection.paths: List<Path>
    get() =
        files.map(File::toPath)

interface R8Parameters : WorkParameters {
    /**
     * Plugin's fat-jar.
     */
    val programJars: ConfigurableFileCollection

    /**
     * Libraries that available on every device.
     */
    val bootClasspathJars: ConfigurableFileCollection

    /**
     * Libraries that available in target application (exteraGram).
     */
    val classpathJars: ConfigurableFileCollection

    /**
     * ProGuard rules.
     */
    val proguardFiles: ConfigurableFileCollection

    /**
     * Minimal SDK version.
     * Same as minSdk at compile time.
     */
    val minSdk: Property<Int>

    /**
     * Disable some optimizations in debug builds.
     */
    val release: Property<Boolean>

    /**
     * Output file that contains merged classpath to avoid collisions at r8 step.
     */
    val mergedClasspathJar: RegularFileProperty

    /**
     * Path to output dex.
     */
    val outputDir: DirectoryProperty
}

abstract class R8WorkAction : WorkAction<R8Parameters> {
    private val logger = Logging.getLogger(R8WorkAction::class.java)

    override fun execute() {
        val output = parameters.outputDir.get().asFile
            .apply { deleteRecursively(); mkdirs() }
            .toPath()

        val classpath = mergeClasspath()

        val command = R8Command.builder(GradleDiagnosticsHandler())
            .setMode(if (parameters.release.get()) CompilationMode.RELEASE else CompilationMode.DEBUG)
            .setMinApiLevel(parameters.minSdk.get())
            .setOutput(output, OutputMode.DexIndexed)
            .addProgramFiles(parameters.programJars.paths)
            .addClasspathFiles(classpath.toPath())
            .addLibraryFiles(parameters.bootClasspathJars.paths)
            .addProguardConfigurationFiles(parameters.proguardFiles.paths)
            // Ignore unresolved errors in telegram jar.
            .addProguardConfiguration(listOf("-ignorewarnings"), Origin.root())
            .build()

        R8.run(command)
    }

    private fun mergeClasspath(): File {
        val merged = parameters.mergedClasspathJar.get().asFile
            .apply { parentFile.mkdirs() }

        val seen = HashSet<String>()

        ZipOutputStream(merged.outputStream().buffered()).use { out ->
            for (jar in parameters.classpathJars.files) {
                if (!jar.isFile) continue

                ZipFile(jar).use { zip ->
                    zip.entries()
                        .asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .filter { seen.add(it.name) }
                        .forEach { entry ->
                            out.putNextEntry(ZipEntry(entry.name))
                            zip.getInputStream(entry).use { it.copyTo(out) }
                            out.closeEntry()
                        }
                }
            }
        }

        return merged
    }

    private inner class GradleDiagnosticsHandler : DiagnosticsHandler {
        override fun info(diagnostic: Diagnostic) = logger.info(diagnostic.diagnosticMessage)
        override fun warning(diagnostic: Diagnostic) = logger.warn(diagnostic.diagnosticMessage)
        override fun error(diagnostic: Diagnostic) = logger.error(diagnostic.diagnosticMessage)
    }
}