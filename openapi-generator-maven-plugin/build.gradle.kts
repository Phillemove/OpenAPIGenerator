plugins {
    kotlin("jvm") version "1.7.0"
    `java-library`
    `maven-publish`
    id("de.benediktritter.maven-plugin-development") version "0.4.0"
}

val mavenUser: String? by project
val mavenPassword: String? by project
// Put Path to Nexus Repository Server here
val path: String = ""

group = "de.phillemove"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("openapi-generator-maven-plugin") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "openapi-generator-maven-plugin"
            url = uri(path)
            credentials {
                username = mavenUser
                password = mavenPassword
            }
        }

        maven {
            name = "openapi-generator-maven-plugin"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.apache.maven:maven-plugin-api:3.8.6")
    implementation("org.apache.maven:maven-core:3.8.6")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.6.4")

    compileOnly("org.jetbrains.kotlin:kotlin-maven-plugin:1.7.10")

    implementation(project(":openapi-generator")){
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}