package com.google.firebase.gradle.plugins

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.json.JSONObject

/**
 * Extension class for [GenerateDocumentationTask].
 *
 * Provides public configurations for the task.
 *
 * @property dackkaJarFile a [File] of the Dackka fat jar
 * @property dependencies a list of all dependent jars (the classpath)
 * @property kotlinSources a list of kotlin source roots
 * @property javaSources a list of java source roots
 * @property suppressedFiles a list of files to exclude from documentation
 * @property outputDirectory where to store the generated files
 */
abstract class GenerateDocumentationTaskExtension : DefaultTask() {
    @get:InputFile
    abstract val dackkaJarFile: Property<File>

    @get:Input
    abstract val dependencies: ListProperty<File>

    @get:InputFiles
    abstract val kotlinSources: ListProperty<File>

    @get:InputFiles
    abstract val javaSources: ListProperty<File>

    @get:InputFiles
    abstract val suppressedFiles: ListProperty<File>

    @get:OutputDirectory
    abstract val outputDirectory: Property<File>
}

/**
 * Task to run Dackka on a project.
 *
 * Since dackka needs to be run on the command line, we have to organize the arguments for dackka into
 * a json file. We then pass that json file to dackka as an argument.
 *
 * @see GenerateDocumentationTaskExtension
 */
abstract class GenerateDocumentationTask @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : GenerateDocumentationTaskExtension() {

    @TaskAction
    fun build() {
        val configFile = saveToJsonFile(constructArguments())
        launchDackka(configFile, workerExecutor)
    }

    private fun constructArguments(): JSONObject {
        // TODO(b/243675474): Move these to a task input for caching purposes
        val linksMap = mapOf(
            "android" to "https://developer.android.com/reference/kotlin/",
            "google" to "https://developer.android.com/reference/",
            "firebase" to "https://firebase.google.com/docs/reference/kotlin/",
            "coroutines" to "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/"
        )

        val jsonMap = mapOf(
            "moduleName" to "",
            "outputDir" to outputDirectory.get().path,
            "globalLinks" to "",
            "sourceSets" to listOf(mutableMapOf(
                "sourceSetID" to mapOf(
                    "scopeId" to "androidx",
                    "sourceSetName" to "main"
                ),
                "sourceRoots" to kotlinSources.get().map { it.path } + javaSources.get().map { it.path },
                "classpath" to dependencies.get().map { it.path },
                "documentedVisibilities" to listOf("PUBLIC", "PROTECTED"),
                "skipEmptyPackages" to "true",
                "suppressedFiles" to suppressedFiles.get().map { it.path },
                "externalDocumentationLinks" to linksMap.map { (name, url) -> mapOf(
                    "url" to url,
                    "packageListUrl" to "file://${project.rootDir.absolutePath}/kotlindoc/package-lists/$name/package-list"
                ) }
            )),
            "offlineMode" to "true",
            "noJdkLink" to "true"
        )

        return JSONObject(jsonMap)
    }

    private fun saveToJsonFile(jsonObject: JSONObject): File {
        val outputFile = File.createTempFile("dackkaArgs", ".json")

        outputFile.deleteOnExit()
        outputFile.writeText(jsonObject.toString(2))

        return outputFile
    }

    private fun launchDackka(argsFile: File, workerExecutor: WorkerExecutor) {
        val workQueue = workerExecutor.noIsolation()

        workQueue.submit(DackkaWorkAction::class.java) {
            args.set(listOf(argsFile.path, "-loggingLevel", "WARN"))
            classpath.set(setOf(dackkaJarFile.get()))
            projectName.set(project.name)
        }
    }
}

/**
 * Parameters needs to launch the Dackka fat jar on the command line.
 *
 * @property args a list of arguments to pass to Dackka- should include the json arguments file
 * @property classpath the classpath to use during execution of the jar file
 * @property projectName name of the calling project, used for the devsite tenant (output directory)
 */
interface DackkaParams : WorkParameters {
    val args: ListProperty<String>
    val classpath: SetProperty<File>
    val projectName: Property<String>
}

/**
 * Work action to launch dackka with a [DackkaParams].
 *
 * Work actions are organized sections of work, offered by gradle.
 */
abstract class DackkaWorkAction @Inject constructor(
    private val execOperations: ExecOperations
) : WorkAction<DackkaParams> {
    override fun execute() {
        execOperations.javaexec {
            mainClass.set("org.jetbrains.dokka.MainKt")
            args = parameters.args.get()
            classpath(parameters.classpath.get())

            environment("DEVSITE_TENANT", "client/${parameters.projectName.get()}")
        }
    }
}
