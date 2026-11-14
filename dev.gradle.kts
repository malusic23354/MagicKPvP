val copyPlugin = tasks.register<Copy>("copyPlugin") {
    description = "Copies plugin from libs to the server folder!"
    from(tasks.named<AbstractArchiveTask>("shadowJar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("run/plugins"))
}

tasks.register<Exec>("runServer") {
    description = "Runs server."
    dependsOn(copyPlugin)
    workingDir = layout.projectDirectory.dir("run").asFile
    commandLine("java", "-Xmx4G", "-jar", "paper.jar", "--nogui")
    standardInput = System.`in`
}