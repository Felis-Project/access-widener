plugins {
    id("felis-dam") version "1.8.0-alpha"
}

group = "felis"
version = "1.0.0-alpha"

dependencies {
    implementation("felis:felis:1.3.0-alpha")
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