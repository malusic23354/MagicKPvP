plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "net.malusic"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.lucko.me/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    paperweight.paperDevBundle("26.3.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation("com.zaxxer:HikariCP:6.3.0")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13") {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "it.unimi.dsi", module = "fastutil")
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "net.malusic.yourplugin.libs.hikari")
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
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
}