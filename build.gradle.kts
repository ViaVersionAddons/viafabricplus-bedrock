import de.florianreuth.baseproject.integration.configureJarInJar
import de.florianreuth.baseproject.integration.includeTransitiveJijDependencies
import de.florianreuth.baseproject.integration.setupFabric
import de.florianreuth.baseproject.setupProject
import de.florianreuth.baseproject.setupViaPublishing

plugins {
    id("net.fabricmc.fabric-loom")
    id("de.florianreuth.baseproject")
}

setupProject()
setupFabric()
setupViaPublishing()

repositories {
    maven("https://repo.viaversion.com")
    maven("https://maven.lenni0451.net/everything")
    maven("https://repo.opencollab.dev/maven-snapshots") {
        content {
            includeGroupByRegex("org\\.cloudburstmc\\..+")
            includeGroup("dev.opencollab")
        }
    }
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.oryxel1")
        }
    }
}

val shade = configureJarInJar()

dependencies {
    implementation("com.viaversion:viafabricplus:5.1.0")

    shade("net.raphimc:ViaBedrock:0.0.31-SNAPSHOT") {
        exclude(group = "com.mojang", module = "brigadier")
        exclude(group = "at.yawk.lz4", module = "lz4-java")
        exclude(group = "io.netty")
    }
    shade("net.raphimc:MinecraftAuth:5.0.2") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    shade("dev.kastle.netty:netty-transport-raknet:1.7.0") {
        exclude(group = "io.netty")
    }
    shade("org.cloudburstmc.netty:netty-transport-nethernet:2.0.0.CR4-20260922.202324-5") {
        exclude(group = "io.netty")
        exclude(group = "org.bouncycastle")
        exclude(group = "dev.opencollab", module = "libdatachannel-java")
    }
    shade("dev.opencollab:libdatachannel-java-arch-detect:0.24.5.0-20260921.140330-12")
}

includeTransitiveJijDependencies()
