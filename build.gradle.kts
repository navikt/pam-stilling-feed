plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("kapt") version "2.4.10"
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

kapt {
    javacOptions {
        option("--enable-preview", "")
    }
}
tasks.register<JavaExec>("runLocal") {
    classpath(sourceSets["test"].runtimeClasspath)
    mainClass = "no.nav.pam.stilling.feed.LocalApplicationKt"
    // Unsafe er for netty og trengs egentlig kun for å kjøre tester
    jvmArgs("--enable-preview","--sun-misc-unsafe-memory-access=allow")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val javalinVersion = "7.2.3"
val micrometerVersion = "1.17.0"
val flywayVersion = "13.3.0"
val jacksonVersion = "2.22.1"
val kafkaVersion = "4.3.1"
val lz4Version = "1.11.1"
dependencies {
    implementation(kotlin("stdlib"))
    implementation(platform("io.javalin:javalin-bom:$javalinVersion"))
    implementation("io.javalin:javalin")
    implementation("io.javalin:javalin-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:1.6.2")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("com.auth0:java-jwt:4.6.0")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.zaxxer:HikariCP:7.1.0")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("no.nav.arbeid.pam:pam-styrk-yrkeskategori-mapper:1.20241202-289c80b8")

    // OpenApi
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion") // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion") // for Swagger UI
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:$javalinVersion") // for ReDoc UI

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("net.bytebuddy:byte-buddy:1.18.11") // Må overstyre mockk sin bytebuddy for å støtte moderne java
}
