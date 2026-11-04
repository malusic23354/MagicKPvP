import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "net.malusic"
version = file("src/main/resources/paper-plugin.yml")
    .readLines()
    .first { it.startsWith("version:") }
    .substringAfter(":")
    .trim()
    .trim('"', '\'')

fun changelogFor(version: String): String {
    val changelog = file("changelog.md")
    if (!changelog.exists()) return "No changelog was specified."

    val lines = changelog.readLines()
    val start = lines.indexOfFirst { it.trim() == "## $version" }
    if (start < 0) return "No changelog entry found for $version."

    val nextHeading = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }
    val end = if (nextHeading < 0) lines.size else start + 1 + nextHeading

    return lines.subList(start + 1, end).joinToString("\n").trim()
}

val localTokenFile = file(".gradle/gradle.properties")
val localToken = if (localTokenFile.exists()) {
    Properties()
        .apply { localTokenFile.inputStream().use { load(it) } }
        .getProperty("modrinthToken")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
} else {
    null
}

val modrinthToken = providers.gradleProperty("modrinthToken")
    .let { provider -> if (localToken != null) provider.orElse(localToken) else provider }

modrinth {
    token.set(providers.gradleProperty("modrinthToken"))
    projectId.set("DXEVhnsR")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    changelog.set(changelogFor(project.version.toString()))
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(listOf(
        "1.21.8", "1.21.9", "1.21.10", "1.21.11",
        "26.1", "26.2", "26.3"
    ))
    loaders.add("paper")
    loaders.add("purpur")
    dependencies {
        required.project("ayRaM8J7")
        optional.project("lKEzGugV")
        optional.project("Vebnzrzj")
        optional.project("DKY9btbd")
    }
}

tasks.named("modrinth") {
    doFirst {
        if (!modrinthToken.isPresent) {
            throw GradleException(
                "No Modrinth token found. Add 'modrinthToken=<token>' to " +
                        File(gradle.gradleUserHomeDir, "gradle.properties")
            )
        }
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.nexomc.com/releases")
}

val paperApi1218 = "io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT"
val paperApi1219 = "io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT"

sourceSets {
    create("v1_21_8")
    create("v1_21_9")
}

dependencies {
    compileOnly(paperApi1218)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.zaxxer:HikariCP:6.3.0")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.nexomc:nexo:1.29.1")

    "v1_21_8CompileOnly"(paperApi1218)
    "v1_21_9CompileOnly"(paperApi1219)

    "v1_21_8CompileOnly"(sourceSets.main.get().output.classesDirs)
    "v1_21_9CompileOnly"(sourceSets.main.get().output.classesDirs)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("plain")
        from(sourceSets["v1_21_8"].output)
        from(sourceSets["v1_21_9"].output)
    }

    shadowJar {
        archiveClassifier.set("")
        from(sourceSets["v1_21_8"].output)
        from(sourceSets["v1_21_9"].output)
        dependsOn("v1_21_8Classes", "v1_21_9Classes")
    }

    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    val copyPlugin = register<Copy>("copyPlugin") {
        from(shadowJar.flatMap { it.archiveFile })
        into(layout.projectDirectory.dir("run/plugins"))
    }

    register<Exec>("runServer") {
        dependsOn(copyPlugin)
        workingDir = layout.projectDirectory.dir("run").asFile
        commandLine("java", "-Xmx4G", "-jar", "paper.jar", "--nogui")
        standardInput = System.`in`
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }

    sourceSets.getByName("v1_21_8").kotlin.srcDir("src/versions/1_21_8")
    sourceSets.getByName("v1_21_9").kotlin.srcDir("src/versions/1_21_9")
}
