import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("kapt") version "1.9.21"
    id("com.gradleup.shadow") version "8.3.2"
    application
}

application {
    mainClass.set("no.nav.pam.stilling.feed.ApplicationKt")
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ShadowJar>{
    mergeServiceFiles()
}

val javalinVersion = "6.5.0"
val testcontainersVersion = "1.20.6"
val micrometerVersion = "1.12.13"
val flywayVersion = "11.3.4"
dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin:javalin-micrometer:$javalinVersion")
    implementation("org.eclipse.jetty:jetty-util")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")

    implementation("ch.qos.logback:logback-classic:1.5.17")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation("org.apache.kafka:kafka-clients:3.8.0")

    implementation("no.nav.arbeid.pam:pam-styrk-yrkeskategori-mapper:1.20241030-dc26b440")

    // OpenApi
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion") // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion") // for Swagger UI
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:$javalinVersion") // for ReDoc UI

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("io.mockk:mockk:1.13.8")
}
