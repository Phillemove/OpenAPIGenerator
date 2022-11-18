import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    `java-library`
    `maven-publish`

}

val mavenUser: String? by project
val mavenPassword: String? by project

group = "de.lambda9"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("openapi-generator") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "openapi-generator"
            url = uri("https://repository.lambda9.de/repository/maven-snapshots")
            credentials {
                username = mavenUser
                password = mavenPassword
            }
        }

        maven {
            name = "openapi-generator"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}


dependencies {
    testImplementation(kotlin("test"))

    implementation("io.ktor:ktor-server-core-jvm:2.1.0")
    implementation("io.ktor:ktor-server-netty-jvm:2.1.0")
    implementation("io.ktor:ktor-server-html-builder:2.1.0")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.1.0")
    implementation("io.ktor:ktor-server-default-headers-jvm:2.1.0")
    implementation("io.ktor:ktor-server-content-negotiation:2.1.0")
    implementation("io.ktor:ktor-server-jetty-jvm:2.1.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.0")
    implementation("io.ktor:ktor-server-test-host-jvm:2.1.0")

    implementation("ch.qos.logback:logback-classic:1.2.11")

    implementation("com.squareup:kotlinpoet:1.12.0")

    implementation("com.reprezen.kaizen:openapi-parser:4.0.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}