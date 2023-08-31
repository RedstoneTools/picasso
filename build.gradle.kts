buildscript {
    repositories {
        maven (url = "https://maven.minecraftforge.net")
        maven (url = "https://repo.spongepowered.org/repository/maven-public/")
        maven (url = "https://plugins.gradle.org/m2/")
        maven (url = "https://jitpack.io")
        mavenCentral()
    }
}

plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

apply(plugin = "java")
apply(plugin = "maven-publish")
apply(plugin = "java-library")

group = "tools.redstone"
version = "1.1"

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16

    withSourcesJar()
    withJavadocJar()
}

task("install") {
    dependsOn(tasks.getByName("publishToMavenLocal"))
}

publishing {
    publications {
        this.create("maven", MavenPublication::class) {
            groupId = project.group as String?
            artifactId = project.name
            version = project.version as String?
            from(components.findByName("java"))
        }
    }
}

repositories {
    mavenCentral()
    maven (url = "https://jitpack.io")
}

dependencies {
    implementation("org.ow2.asm:asm:9.5")
    implementation("org.ow2.asm:asm-tree:9.4")
    implementation("org.ow2.asm:asm-util:9.5")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

tasks.test {
    useJUnitPlatform()
}