plugins {
    id("felis-dam") version "1.9.1-alpha"
    `maven-publish`
}

group = "felis"
version = "1.1.0-alpha"

loaderMake {
    version = "1.20.6"
}

repositories {
    mavenLocal()
}

dependencies {
    implementation("felis:felis:1.4.0-alpha")
    implementation("net.fabricmc:access-widener:2.1.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    filesMatching("mods.toml") {
        expand("version" to version)
    }
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                artifactId = "access-widener"
            }
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "Repsy"
            url = uri("https://repo.repsy.io/mvn/0xjoemama/public")
            credentials {
                username = System.getenv("REPSY_USERNAME")
                password = System.getenv("REPSY_PASSWORD")
            }
        }
    }
}
