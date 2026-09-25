plugins {
    alias(libs.plugins.fabric.loom)
    java
}

version = "2.0"
group = "com.wynncompare"

base {
    archivesName.set("wynncompare")
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft(libs.minecraft)
    mappings("net.fabricmc:yarn:${libs.versions.yarn.mappings.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}
