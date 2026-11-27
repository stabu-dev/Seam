import mindustry.client.*
import mindustry.client.service.*
import mindustry.client.task.*
import java.util.zip.*

buildscript{
    val gameVersion = providers.gradleProperty("mindustryVersion").get()
    repositories{
        ivy{
            url = uri("https://github.com/")
            patternLayout{ artifact("/Anuken/Mindustry/releases/download/[revision]/dependencies.jar") }
            metadataSources{ artifact() }
            content{ includeVersion("Anuken", "Mindustry", gameVersion) }
        }
    }
    configurations.classpath{
        resolutionStrategy.eachDependency{
            if(requested.group == "Anuken" && requested.name == "Mindustry") useVersion(gameVersion)
        }
    }
}

plugins{
    java
    `maven-publish`
    id("com.github.GglLfr.MindustryClient") version "v1.1.3"
}

val mindustryVersion = providers.gradleProperty("mindustryVersion").get()
require(mindustryVersion.matches(Regex("v\\d+(\\.\\d+)*"))){ "Pin an explicit Mindustry release." }

group = "com.github.stabu-dev"
version = providers.gradleProperty("artifactVersion").orNull
    ?: Regex("""(?m)^\s*version:\s*([^\r\n#]+)""")
        .find(file("mod.hjson").readText())?.groupValues?.get(1)?.trim()
            ?: throw GradleException("Missing version in mod.hjson")

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val sdkRoot: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val projectName = project.name

sourceSets.main{
    java.setSrcDirs(listOf("src"))
}

repositories{
    mavenCentral()

    //Downloads the dependencies JAR file from Mindustry releases; does not use any real repository. Surprisingly, this is the most reliable option.
    ivy{
        url = uri("https://github.com/")
        patternLayout{
            artifact("/Anuken/Mindustry/releases/download/[revision]/dependencies(-[classifier]).jar")
        }
        metadataSources{ artifact() }

        content{
            includeVersion("Anuken", "Mindustry", mindustryVersion)
        }
    }
}

java{
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach{
    options.encoding = "UTF-8"
    options.release.set(17)
}

val javadocClasspath = tasks.register("javadocClasspath"){
    val output = layout.buildDirectory.file("tmp/javadoc/mindustry-classes.jar")
    inputs.files(configurations.compileClasspath)
    outputs.file(output)
    doLast{
        val target = output.get().asFile
        target.parentFile.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use{ out ->
            val seen = HashSet<String>()
            configurations.compileClasspath.get().files
                .filter{ it.isFile && it.extension == "jar" }
                .forEach{ jar ->
                    ZipFile(jar).use{ zip ->
                        for(entry in zip.entries()){
                            if(entry.isDirectory || entry.name.endsWith(".java") || !seen.add(entry.name)) continue
                            out.putNextEntry(ZipEntry(entry.name))
                            zip.getInputStream(entry).use{ it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                }
        }
    }
}

tasks.withType<Javadoc>().configureEach{
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply{
        addBooleanOption("Xdoclint:none", true)
        addStringOption("-release", "17")
    }
}

tasks.named<Javadoc>("javadoc"){
    dependsOn(javadocClasspath, configurations.compileClasspath)
    classpath = files(
        sourceSets.main.get().output,
        javadocClasspath.map{ it.outputs.files },
        provider{ configurations.compileClasspath.get().files.filter{ it.isDirectory } }
    )
}

tasks.withType<Jar>().configureEach{
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies{
    compileOnly(project(":annotations"))
    annotationProcessor(project(":annotations"))
    compileOnly("Anuken:Mindustry:$mindustryVersion")
}

publishing.publications.register<MavenPublication>("maven"){
    from(components["java"])
}

val client = gradle.sharedServices.registerIfAbsent(MindustryClientPlugin.serviceName, MindustryClientService::class.java){}
val install = tasks.register<Copy>("install"){
    description = "Installs Seam into the detected client's mods directory."
    usesService(client)
    from(tasks.jar)
    into(client.map{ it.detected.modsDirectory })
}

tasks.withType<RunClientTask>().configureEach{
    dependsOn(install)
}

tasks.check{
    doLast{
        check(tasks.named<InstallClientTask>("installClient").get().buildNumber.get() == mindustryVersion.removePrefix("v")){
            "The development client must match the pinned Mindustry dependency."
        }
    }
}

tasks.jar{
    archiveFileName.set("${projectName}Desktop.jar")

    from(configurations.runtimeClasspath.map{ files ->
        files.map{ if(it.isDirectory) it else zipTree(it) }
    })

    from(projectDir){
        include("mod.hjson")
        include("icon.png")
    }

    metaInf.from(file("LICENSE"))

    from("assets/"){
        include("**")
    }
}

fun androidSdk(): File{
    val sdk = sdkRoot?.let{ File(it) }
    if(sdk == null || !sdk.exists()){
        throw GradleException("No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.")
    }
    return sdk
}

/** Dexes and desugars [input] into [output] with d8 from ANDROID_HOME build-tools or PATH. */
fun dex(input: File, output: File, additionalClasspath: Collection<File> = emptyList()){
    val sdk = androidSdk()
    val platformRoot = File(sdk, "platforms").listFiles()
        ?.sortedDescending()
        ?.firstOrNull{ File(it, "android.jar").exists() }
        ?: throw GradleException("No android.jar found. Ensure that you have an Android platform installed.")
    val d8Name = if(isWindows) "d8.bat" else "d8"
    val d8 = File(sdk, "build-tools").listFiles()
        ?.sortedDescending()
        ?.map{ File(it, d8Name) }
        ?.firstOrNull{ it.isFile }
        ?.path ?: d8Name

    //collect dependencies needed for desugaring
    val classpath = configurations.compileClasspath.get().files + configurations.runtimeClasspath.get().files + additionalClasspath

    val command = buildList{
        add(d8)
        classpath.forEach{ addAll(listOf("--classpath", it.path)) }
        addAll(listOf("--lib", File(platformRoot, "android.jar").path))
        addAll(listOf("--min-api", "21"))
        addAll(listOf("--output", output.path))
        add(input.path)
    }

    val execution = providers.exec{
        commandLine(command)
        isIgnoreExitValue = true
    }
    val exitCode = execution.result.get().exitValue
    val diagnostics = execution.standardOutput.asText.get() + execution.standardError.asText.get()
    val log = layout.buildDirectory.file("reports/d8/${input.name}.log").get().asFile
    log.parentFile.mkdirs()
    log.writeText(diagnostics)
    if(exitCode != 0) throw GradleException("d8 failed with exit code $exitCode; see $log\n${diagnostics.take(4000)}")
    if(diagnostics.isNotBlank()) logger.lifecycle(diagnostics)
}

val jarAndroid = tasks.register("jarAndroid"){
    dependsOn(tasks.jar)
    val output = layout.buildDirectory.file("tmp/jarAndroid/${projectName}Android.jar")
    inputs.file(tasks.jar.flatMap{ it.archiveFile })
    outputs.file(output)

    doLast{
        val target = output.get().asFile
        target.parentFile.mkdirs()
        dex(tasks.jar.get().archiveFile.get().asFile, target)
    }
}

tasks.register<Jar>("deploy"){
    dependsOn(jarAndroid, tasks.jar)
    archiveFileName.set("$projectName.jar")

    from(tasks.jar.map{ zipTree(it.archiveFile) })
    from(jarAndroid.map{ zipTree(it.outputs.files.singleFile) })
}
