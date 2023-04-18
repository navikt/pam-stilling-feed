plugins {
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:5.4.2")
    implementation("io.javalin:javalin-micrometer:5.4.2")
    implementation("org.eclipse.jetty:jetty-util")
    implementation("io.micrometer:micrometer-core:1.10.6")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.6")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

    implementation("ch.qos.logback:logback-classic:1.4.6")
    implementation("net.logstash.logback:logstash-logback-encoder:7.3")

    implementation("org.flywaydb:flyway-core:9.16.3")
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    implementation("org.apache.kafka:kafka-clients:3.4.0")

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:testcontainers:1.18.0")
    testImplementation("org.testcontainers:kafka:1.18.0")
    testImplementation("org.testcontainers:postgresql:1.18.0")
    testImplementation("org.testcontainers:junit-jupiter:1.18.0")
    testImplementation("io.mockk:mockk:1.13.5")

}
