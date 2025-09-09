import java.net.URI

plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.1.0"
    id("xyz.wagyourtail.commons-gradle") version "1.0.5-SNAPSHOT"
}

version = if (project.hasProperty("version_snapshot")) project.properties["version"] as String + "-SNAPSHOT" else project.properties["version"] as String
group = project.properties["maven_group"] as String

commons.autoToolchain(8, 17)

base {
    archivesName.set(project.properties["archives_base_name"] as String)
}

repositories {
    mavenCentral()
    maven("https://maven.quiltmc.org/repository/release")
}

dependencies {

    // class transform
    implementation("net.lenni0451.classtransform:core:1.14.1")

    // qup
    implementation("org.quiltmc.qup:json:0.2.0") {
        isTransitive = false
    }

    testImplementation("org.ow2.asm:asm:9.2")
    testImplementation("org.ow2.asm:asm-commons:9.2")
    testImplementation("org.ow2.asm:asm-tree:9.2")

    testImplementation("net.lenni0451:Reflect:1.5.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "xyz.wagyourtail.unimined.jarmodagent.JarModAgent",
            "Main-Class" to "xyz.wagyourtail.unimined.jarmodagent.JarModAgent",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.shadowJar {
    relocate("org.objectweb", "xyz.wagyourtail.unimined.jarmodagent.shadow.org.objectweb")
    relocate("org.quiltmc.qup.json", "xyz.wagyourtail.unimined.jarmodagent.shadow.org.quiltmc.qup.json")
}

publishing {
    repositories {
        maven {
            name = "WagYourMaven"
            url = if (project.hasProperty("version_snapshot")) {
                URI.create("https://maven.wagyourtail.xyz/snapshots/")
            } else {
                URI.create("https://maven.wagyourtail.xyz/releases/")
            }
            credentials {
                username = project.findProperty("mvn.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("mvn.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = project.properties["archives_base_name"] as String? ?: project.name
            version = project.version as String

            artifact(tasks["jar"]) {}
            artifact(tasks["shadowJar"]) {
                classifier = "all"
            }
        }
    }
}
