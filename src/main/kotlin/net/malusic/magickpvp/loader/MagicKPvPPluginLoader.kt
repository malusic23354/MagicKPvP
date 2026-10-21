package net.malusic.magickpvp.loader

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class MagicKPvPPluginLoader : PluginLoader {

    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()

        for (coordinate in LIBRARIES) {
            resolver.addDependency(Dependency(DefaultArtifact(coordinate), null))
        }
        resolver.addRepository(
            RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
            ).build()
        )

        classpathBuilder.addLibrary(resolver)
    }

    private companion object {
        val LIBRARIES = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib:2.2.20",
            "com.zaxxer:HikariCP:6.3.0",
            "org.xerial:sqlite-jdbc:3.51.3.0",
            "com.mysql:mysql-connector-j:9.7.0"
        )
    }
}
