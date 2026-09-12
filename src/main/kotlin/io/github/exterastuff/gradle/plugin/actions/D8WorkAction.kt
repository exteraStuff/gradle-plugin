package io.github.exterastuff.gradle.plugin.actions

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/** Timestamp written into every output entry. Used to keep output byte-identical. */
private val ZIP_EPOCH = java.nio.file.attribute.FileTime.fromMillis(315_532_800_000L)

/** Entries of the source jar that are replaced by the dex or invalidated by it. */
private val SIGNATURE_SUFFIXES = listOf(".SF", ".DSA", ".RSA", ".EC")

private val ConfigurableFileCollection.paths: List<Path>
    get() = files.map(File::toPath)

private val ZipEntry.isResource: Boolean
    get() =
        !isDirectory &&
            !name.endsWith(".class") &&
            name != "META-INF/MANIFEST.MF" &&
            !(name.startsWith("META-INF/") && SIGNATURE_SUFFIXES.any(name::endsWith))

interface D8Parameters : WorkParameters {
    /** Maven coordinates of the dependency, `group:artifact:version`. */
    val coordinates: Property<String>

    /** Jar of the dependency, as it comes from the resolved classpath. */
    val inputJar: RegularFileProperty

    /** Libraries that available on every device. */
    val bootClasspathJars: ConfigurableFileCollection

    /** Libraries the dependency compiles against, needed to desugar it. */
    val classpathJars: ConfigurableFileCollection

    /** Minimal SDK version. Same as minSdk at compile time. */
    val minSdk: Property<Int>

    /** Disable some optimizations in debug builds. */
    val release: Property<Boolean>

    /** Directory the raw dex files are written to before packing. */
    val workDir: DirectoryProperty

    /** Jar holding the dex and the resources of the dependency. */
    val outputJar: RegularFileProperty
}

/** Converts a single dependency jar into a jar that carries dex instead of classes. */
abstract class D8WorkAction : WorkAction<D8Parameters> {
    private val logger = Logging.getLogger(D8WorkAction::class.java)

    override fun execute() {
        val input = parameters.inputJar.get().asFile

        val work =
            parameters.workDir.get().asFile.apply {
                deleteRecursively()
                mkdirs()
            }

        val command =
            D8Command.builder(GradleDiagnosticsHandler())
                .setMode(
                    if (parameters.release.get()) CompilationMode.RELEASE else CompilationMode.DEBUG
                )
                .setMinApiLevel(parameters.minSdk.get())
                .setOutput(work.toPath(), OutputMode.DexIndexed)
                .addProgramFiles(input.toPath())
                // remove dependency from its compile classpath
                .addClasspathFiles(parameters.classpathJars.paths.filter { it != input.toPath() })
                .addLibraryFiles(parameters.bootClasspathJars.paths)
                .build()

        D8.run(command)

        pack(input, work)
    }

    /** Puts the dex next to everything the source jar carried besides classes. */
    private fun pack(input: File, work: File) {
        val output = parameters.outputJar.get().asFile.apply { parentFile.mkdirs() }

        val dexFiles =
            work.listFiles().orEmpty().filter { it.name.endsWith(".dex") }.sortedBy(File::getName)

        ZipOutputStream(output.outputStream().buffered()).use { out ->
            out.setLevel(Deflater.BEST_COMPRESSION)

            out.writeEntry("META-INF/MANIFEST.MF", manifestBytes())

            for (dex in dexFiles) out.writeEntry(dex.name, dex.readBytes())

            ZipFile(input).use { zip ->
                zip.entries().asSequence().filter(ZipEntry::isResource).forEach { entry ->
                    out.writeEntry(entry.name, zip.getInputStream(entry).use { it.readBytes() })
                }
            }
        }

        logger.lifecycle("write ${output.path} (${dexFiles.size} dex)")
    }

    /** Manifest that makes the jar describe itself once it is unpacked from the plugin. */
    private fun manifestBytes(): ByteArray {
        val coordinates = parameters.coordinates.get()

        val manifest =
            Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes.putValue("Fat-Jar-Id", coordinates.substringBeforeLast(':'))
                mainAttributes.putValue("Fat-Jar-Version", coordinates.substringAfterLast(':'))
            }

        return ByteArrayOutputStream().apply { use(manifest::write) }.toByteArray()
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name).apply { lastModifiedTime = ZIP_EPOCH })
        write(bytes)
        closeEntry()
    }

    private inner class GradleDiagnosticsHandler : DiagnosticsHandler {
        override fun info(diagnostic: Diagnostic) = logger.info(diagnostic.diagnosticMessage)

        override fun warning(diagnostic: Diagnostic) = logger.warn(diagnostic.diagnosticMessage)

        override fun error(diagnostic: Diagnostic) = logger.error(diagnostic.diagnosticMessage)
    }
}
