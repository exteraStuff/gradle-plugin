package io.github.exterastuff.gradle.plugin

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.exterastuff.gradle.plugin.extensions.ExteraExtension
import io.github.exterastuff.gradle.plugin.tasks.BuildDexTask
import io.github.exterastuff.gradle.plugin.tasks.DexProvidedServicesTask
import io.github.exterastuff.gradle.plugin.tasks.DexProvidedServicesTask.ProvidedServiceSpec
import io.github.exterastuff.gradle.plugin.tasks.ProcessTelegramJarTask
import io.github.exterastuff.gradle.plugin.tasks.SignJarTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

@Suppress("UnstableApiUsage")
abstract class ExteraPlugin : Plugin<Project> {
    private companion object {
        const val EXTENSION_NAME = "extera"
        const val TASK_GROUP = "extera"

        const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
        const val R8_CONFIGURATION = "exteraR8"

        const val PROVIDED_SERVICE_CONFIGURATION = "providedService"
        const val REQUIRED_SERVICE_CONFIGURATION = "requiredService"
        const val SERVICES_DIRECTORY = "services"

        val SEMVER = Regex("""\d+\.\d+\.\d+""")

        val ARTIFACT_TYPE: Attribute<String> = Attribute.of("artifactType", String::class.java)
        const val ANDROID_CLASSES_JAR = "android-classes-jar"
    }

    override fun apply(target: Project) {
        val extension =
            target.extensions.create<ExteraExtension>(EXTENSION_NAME).apply {
                dexOutputDir.convention(target.layout.buildDirectory.dir("outputs/dex"))
                jarOutputDir.convention(target.layout.buildDirectory.dir("outputs/jar"))
            }

        target.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
            target.configureExtension(extension)
        }
    }

    private fun Project.configureExtension(extension: ExteraExtension) {
        val r8 = configurations.resolvable(R8_CONFIGURATION)

        // use specified version of r8 in project
        dependencies.addProvider(
            R8_CONFIGURATION,
            extension.r8.version.map { "com.android.tools:r8:$it" },
        )

        val providedServiceDependencies =
            serviceDependencyScope(
                PROVIDED_SERVICE_CONFIGURATION,
                "Service definitions shipped inside the plugin jar as separate dexed jars",
            )

        val requiredServiceDependencies =
            serviceDependencyScope(
                REQUIRED_SERVICE_CONFIGURATION,
                "Service definitions the plugin expects to already be present on the client",
            )

        val processTelegramJar =
            tasks.register<ProcessTelegramJarTask>("processTelegramJar") {
                group = TASK_GROUP
                description =
                    "Fix access modifiers for inner classes and strip conflicting packages"

                inputJar.set(extension.telegram.jar)
                excludedPrefixes.set(extension.telegram.conflictingPackages)
                outputJar.set(
                    layout.buildDirectory.file("intermediates/telegram/Telegram.stripped.jar")
                )
            }

        // Add Telegram jar to compile classpath.
        dependencies.add("compileOnly", files(processTelegramJar.flatMap { it.outputJar }))

        val buildDexAll =
            tasks.register("buildDex") {
                group = TASK_GROUP
                description = "Builds the dex of every variant"
            }

        project.afterEvaluate {
            if (!extension.bundleConfigured.get()) return@afterEvaluate

            tasks.register("packagePluginJar") {
                group = TASK_GROUP
                description = "Packages every variant into a plugin jar"
            }

            if (
                !extension.bundle.signing.debugConfigured.get() &&
                    !extension.bundle.signing.releaseConfigured.get()
            )
                return@afterEvaluate

            tasks.register("signPluginJar") {
                group = TASK_GROUP
                description = "Signs every variant of a plugin jar"
            }
        }

        val androidComponents = extensions.getByType<LibraryAndroidComponentsExtension>()

        androidComponents.onVariants { variant ->
            val variantName = variant.name
            val variantTitle = variantName.replaceFirstChar(Char::uppercase)

            val isRelease = variant.buildType == "release"

            val compileJars = classesJarsOf("${variantName}CompileClasspath")
            val runtimeJars = classesJarsOf("${variantName}RuntimeClasspath")

            val shadedJar =
                tasks.register<ShadowJar>("shade$variantTitle") {
                    group = TASK_GROUP
                    description = "Assembles the relocated fat jar of the $variantName variant"

                    destinationDirectory.set(layout.buildDirectory.dir("intermediates/shaded"))
                    archiveFileName.set("classes-$variantName.jar")

                    mergeServiceFiles()

                    filesMatching(listOf("META-INF/*.kotlin_module", "META-INF/services/**")) {
                        duplicatesStrategy = DuplicatesStrategy.INCLUDE
                    }

                    from(
                        tasks.named<KotlinCompileTool>("compile${variantTitle}Kotlin").flatMap {
                            it.destinationDirectory
                        }
                    )
                    from(
                        tasks.named<JavaCompile>("compile${variantTitle}JavaWithJavac").flatMap {
                            it.destinationDirectory
                        }
                    )
                    from(runtimeJars.map { jars -> jars.map(::zipTree) })

                    val shadedPackage = extension.shadow.targetPackage.get()

                    for (spec in extension.shadow.relocations.get()) {
                        val pkg = spec.pkg + "."

                        relocate(pkg, "$shadedPackage.$pkg") {
                            spec.excludes.get().forEach(::exclude)
                        }
                    }
                }

            val buildDexVariant =
                tasks.register<BuildDexTask>("buildDex$variantTitle") {
                    group = TASK_GROUP
                    description = "Compiles the $variantName fat jar into dex"

                    programJars.from(shadedJar)
                    bootClasspathJars.from(androidComponents.sdkComponents.bootClasspath)

                    classpathJars.from(
                        compileJars.map { it.minus(runtimeJars.get()) },
                        processTelegramJar.flatMap { it.outputJar },
                    )

                    proguardFiles.from(extension.r8.proguardFiles)
                    relocatedPackages.set(
                        extension.shadow.relocations.map { specs -> specs.map { it.pkg } }
                    )
                    r8Classpath.from(r8)

                    minSdk.set(extension.r8.minSdk.orElse(variant.minSdk.apiLevel))
                    release.set(isRelease)

                    mergedClasspathJar.set(
                        layout.buildDirectory.file(
                            "intermediates/dex-classpath/$variantName/classpath.jar"
                        )
                    )
                    outputDir.set(extension.dexOutputDir.map { it.dir(variantName) })
                }

            buildDexAll.configure { dependsOn(buildDexVariant) }

            project.afterEvaluate {
                if (!extension.bundleConfigured.get()) return@afterEvaluate

                val providedServiceSpecs =
                    providedServiceSpecsOf(variantName, providedServiceDependencies)

                val requiredServices =
                    coordinatesOf(serviceClasspathOf(variantName, requiredServiceDependencies))

                val dexProvidedServices =
                    tasks.register<DexProvidedServicesTask>("dexProvidedServices$variantTitle") {
                        group = TASK_GROUP
                        description = "Converts the $variantName provided services into dexed jars"

                        providedServices.set(providedServiceSpecs)

                        bootClasspathJars.from(androidComponents.sdkComponents.bootClasspath)
                        classpathJars.from(compileJars, processTelegramJar.flatMap { it.outputJar })
                        r8Classpath.from(r8)

                        minSdk.set(extension.r8.minSdk.orElse(variant.minSdk.apiLevel))
                        release.set(isRelease)

                        workDir.set(
                            layout.buildDirectory.dir("intermediates/services-dex/$variantName")
                        )
                        outputDir.set(
                            layout.buildDirectory.dir("intermediates/services/$variantName")
                        )
                    }

                val packageJarVariant =
                    tasks.register<Jar>("packagePluginJar$variantTitle") {
                        group = TASK_GROUP
                        description = "Packages the $variantName dex into a plugin jar"

                        destinationDirectory.set(extension.jarOutputDir)
                        archiveBaseName.set(extension.bundle.manifest.id)
                        archiveVersion.set(extension.bundle.manifest.version)
                        archiveClassifier.set(if (isRelease) "" else variantName)

                        isPreserveFileTimestamps = true
                        isReproducibleFileOrder = true

                        from(buildDexVariant.flatMap { it.outputDir })
                        from(dexProvidedServices.flatMap { it.outputDir }) {
                            into(SERVICES_DIRECTORY)
                        }

                        manifest {
                            extension.bundle.manifest.apply {
                                attributes(
                                    "Plugin-Id" to id.get(),
                                    "Plugin-Name" to name.get(),
                                    "Plugin-Description" to description.get(),
                                    "Plugin-Icon" to icon.get(),
                                    "Plugin-Author" to author.get(),
                                    "Plugin-Version" to version.get(),
                                    "Plugin-Min-Client-Version" to minClientVersion.get(),
                                    "Plugin-Class" to entryClass.get(),
                                    "Plugin-Update-Sources" to updateSources.get().joinEntries("="),
                                    "Plugin-Dependencies" to
                                        requireSemver(dependencies.get()).joinEntries(":"),
                                    "Plugin-Provided-Services" to
                                        providedServiceSpecs.map { specs ->
                                            specs.joinToString(", ") { it.coordinates.get() }
                                        },
                                    "Plugin-Required-Services" to
                                        requiredServices.map { it.joinToString(", ") },
                                )
                            }
                        }
                    }

                tasks.getByName("packagePluginJar").dependsOn(packageJarVariant)

                if (
                    (!isRelease && !extension.bundle.signing.debugConfigured.get()) ||
                        (isRelease && !extension.bundle.signing.releaseConfigured.get())
                )
                    return@afterEvaluate

                val signJarVariant =
                    tasks.register<SignJarTask>("signPluginJar$variantTitle") {
                        group = TASK_GROUP
                        description = "Signs the $variantName plugin jar"

                        val archiveFile = packageJarVariant.flatMap { it.archiveFile }

                        unsignedJar.set(archiveFile)

                        signedJar.set(
                            extension.jarOutputDir.map {
                                with(extension.bundle.manifest) {
                                    val sb = StringBuilder("${id.get()}-${version.get()}")

                                    if (!isRelease) sb.append("-debug")

                                    sb.append("-signed")
                                    sb.append(".jar")

                                    it.file(sb.toString())
                                }
                            }
                        )

                        val variant =
                            if (isRelease) extension.bundle.signing.release
                            else extension.bundle.signing.debug

                        keyStorePath.set(variant.keyStore.path)
                        keyStoreAlias.set(variant.keyStore.alias)
                        storePassword.set(variant.keyStore.storePassword)
                        keyPassword.set(variant.keyStore.keyPassword)
                        tsaUrls.set(variant.tsaUrls)
                    }

                tasks.getByName("signPluginJar").dependsOn(signJarVariant)
            }
        }
    }

    /**
     * Get list of .jar files in provided configuration. artifactView is required for getting .jar
     * files from .aar libraries.
     */
    private fun Project.classesJarsOf(configurationName: String): Provider<FileCollection> =
        configurations.named(configurationName).map { configuration ->
            configuration.incoming
                .artifactView {
                    attributes.attribute(ARTIFACT_TYPE, ANDROID_CLASSES_JAR)
                    lenient(true)
                }
                .files
        }

    private fun Project.serviceDependencyScope(
        name: String,
        scopeDescription: String,
    ): NamedDomainObjectProvider<DependencyScopeConfiguration> {
        val scope =
            configurations.dependencyScope(name) {
                description = scopeDescription

                // The service ships whole, as a single artifact, and the plugin
                // only compiles against it.
                withDependencies {
                    forEach { dependency ->
                        (dependency as? ModuleDependency)?.isTransitive = false
                    }
                }
            }

        configurations.named("compileOnly") { extendsFrom(scope.get()) }

        return scope
    }

    private fun Project.serviceClasspathOf(
        variantName: String,
        dependencies: NamedDomainObjectProvider<DependencyScopeConfiguration>,
    ): NamedDomainObjectProvider<ResolvableConfiguration> {
        val variantTitle = variantName.replaceFirstChar(Char::uppercase)

        return configurations.resolvable("${dependencies.name}${variantTitle}Classpath") {
            description =
                "Resolves the ${dependencies.name} definitions of the $variantName variant"

            extendsFrom(dependencies.get())

            val compileClasspath =
                configurations.getByName("${variantName}CompileClasspath").attributes

            for (key in compileClasspath.keySet()) {
                @Suppress("UNCHECKED_CAST")
                attributes.attribute(key as Attribute<Any>, compileClasspath.getAttribute(key)!!)
            }
        }
    }

    private fun coordinatesOf(
        classpath: NamedDomainObjectProvider<ResolvableConfiguration>
    ): Provider<List<String>> = classpath.flatMap { configuration ->
        configuration.incoming.resolutionResult.rootComponent.map { root ->
            coordinatesOf(root).filterKeys { it != root.id }.values.sorted()
        }
    }

    private fun Project.providedServiceSpecsOf(
        variantName: String,
        dependencies: NamedDomainObjectProvider<DependencyScopeConfiguration>,
    ): Provider<List<ProvidedServiceSpec>> {
        val objectFactory = objects

        return serviceClasspathOf(variantName, dependencies).flatMap { configuration ->
            val incoming = configuration.incoming

            val artifacts =
                incoming
                    .artifactView {
                        attributes.attribute(ARTIFACT_TYPE, ANDROID_CLASSES_JAR)
                        lenient(true)
                    }
                    .artifacts
                    .resolvedArtifacts

            incoming.resolutionResult.rootComponent.zip(artifacts) { root, resolved ->
                val coordinates = coordinatesOf(root)

                resolved
                    .map { artifact ->
                        val id = artifact.id.componentIdentifier

                        val artifactCoordinates =
                            coordinates[id]
                                ?: throw GradleException(
                                    "providedService dependency $id has no maven coordinates"
                                )

                        objectFactory.newInstance(ProvidedServiceSpec::class.java).apply {
                            this.coordinates.set(artifactCoordinates)
                            jar.set(artifact.file)
                        }
                    }
                    .sortedBy { it.coordinates.get() }
            }
        }
    }

    private fun coordinatesOf(root: ResolvedComponentResult): Map<ComponentIdentifier, String> {
        val coordinates = hashMapOf<ComponentIdentifier, String>()
        val visited = hashSetOf<ComponentIdentifier>()

        fun visit(component: ResolvedComponentResult) {
            if (!visited.add(component.id)) return

            component.moduleVersion?.let {
                coordinates[component.id] = "${it.group}:${it.name}:${it.version}"
            }

            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
                visit(it.selected)
            }
        }

        visit(root)

        return coordinates
    }

    private fun requireSemver(dependencies: Map<String, String>): Map<String, String> {
        for ((id, version) in dependencies) {
            if (!SEMVER.matches(version))
                throw GradleException(
                    "Version '$version' of plugin dependency '$id' is not a major.minor.patch version"
                )
        }

        return dependencies
    }

    private fun Map<String, String>.joinEntries(separator: String): String =
        toSortedMap().map { (key, value) -> "$key$separator$value" }.joinToString(", ")
}
