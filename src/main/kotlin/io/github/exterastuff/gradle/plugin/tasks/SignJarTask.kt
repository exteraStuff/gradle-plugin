package io.github.exterastuff.gradle.plugin.tasks

import jdk.security.jarsigner.JarSigner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.security.KeyStore
import java.util.zip.ZipFile

abstract class SignJarTask : DefaultTask() {
    @get:InputFile
    abstract val unsignedJar: RegularFileProperty

    @get:OutputFile
    abstract val signedJar: RegularFileProperty


    @get:InputFile
    abstract val keyStorePath: RegularFileProperty

    @get:Input
    abstract val keyStoreAlias: Property<String>

    @get:Internal
    abstract val storePassword: Property<String>

    @get:Internal
    abstract val keyPassword: Property<String>


    @get:Input
    abstract val tsaUrls: ListProperty<String>

    @TaskAction
    fun run() {
        val storePass = storePassword.get().toCharArray()
        val keyPass = keyPassword.orNull?.toCharArray()
            ?: storePass

        val keyStore = KeyStore.getInstance(keyStorePath.get().asFile, storePass)

        val keyStoreEntry =
            keyStore.getEntry(keyStoreAlias.get(), KeyStore.PasswordProtection(keyPass))
                    as? KeyStore.PrivateKeyEntry
                ?: throw GradleException("Alias '${keyStoreAlias.get()}' doesn't have private key entry")

        val builder = JarSigner.Builder(keyStoreEntry)
            .digestAlgorithm("SHA-256")
            .signatureAlgorithm("SHA256withRSA")
            .signerName("DEXBUNDLE")

        val urls = tsaUrls.get()

        if (urls.isEmpty()) {
            signOnce(builder)
            logger.warn("No TSA URLs provided")
            return
        }

        for (url in urls) {
            builder.tsa(URI(url))
            builder.setProperty("tsaDigestAlg", "SHA-256")

            try {
                signOnce(builder)
                logger.lifecycle("Timestamp was received from $url")
                return
            } catch (e: Throwable) {
                logger.warn("TSA $url is unavailable", e)
            }
        }

        throw GradleException("No TSA are available")
    }

    private fun signOnce(builder: JarSigner.Builder) {
        ZipFile(unsignedJar.get().asFile).use { input ->
            val outputFile = signedJar.get().asFile
            outputFile.delete()

            outputFile
                .outputStream()
                .buffered()
                .use { output -> builder.build().sign(input, output) }
        }
    }
}