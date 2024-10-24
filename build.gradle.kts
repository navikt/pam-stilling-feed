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

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:6.3.0")
    implementation("io.javalin:javalin-micrometer:6.3.0")
    implementation("org.eclipse.jetty:jetty-util")
    implementation("io.micrometer:micrometer-core:1.12.4")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.4")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")

    implementation("ch.qos.logback:logback-classic:1.5.11")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("org.flywaydb:flyway-core:10.2.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.2.0")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.zaxxer:HikariCP:5.1.0")

    implementation("org.apache.kafka:kafka-clients:3.8.0")

    implementation("no.nav.arbeid.pam:pam-styrk-yrkeskategori-mapper:1.20240828-2971bbff")

    // OpenApi
    kapt("io.javalin.community.openapi:openapi-annotation-processor:6.3.0")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:6.3.0") // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:6.3.0") // for Swagger UI
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:6.3.0") // for ReDoc UI

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:testcontainers:1.19.6")
    testImplementation("org.testcontainers:kafka:1.19.6")
    testImplementation("org.testcontainers:postgresql:1.19.6")
    testImplementation("org.testcontainers:junit-jupiter:1.19.6")
    testImplementation("io.mockk:mockk:1.13.8")
}
