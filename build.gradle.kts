plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

group = "io.github.ravisorathiya"
version = "0.9.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}

// Bytecode for Java 17+ (what Android Studio ships), built with whatever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

application {
    applicationName = "kotlin-artisan"
    mainClass = "io.github.ravisorathiya.artisan.MainKt"
}

// A second launcher named `artisan`, next to `kotlin-artisan`, in bin/.
val artisanScripts = tasks.register<CreateStartScripts>("artisanStartScripts") {
    applicationName = "artisan"
    mainClass = application.mainClass
    outputDir = layout.buildDirectory.dir("artisan-scripts").get().asFile
    classpath = tasks.startScripts.get().classpath
}
distributions.main {
    contents {
        from(artisanScripts) { into("bin") }
        from("LICENSE", "README.md")
        // PowerShell 7 predictor (C#), when built first: dotnet publish predictor -c Release -o predictor/bin/Release/publish
        from("predictor/bin/Release/publish") {
            include("ArtisanPredictor.dll")
            into("predictor")
        }
    }
}

tasks.jar {
    // -PreportedVersion=x.y.z builds a copy that thinks it is x.y.z (used to test self:update locally).
    manifest { attributes("Implementation-Version" to (findProperty("reportedVersion") ?: project.version)) }
}

tasks.test {
    useJUnitPlatform()
}
