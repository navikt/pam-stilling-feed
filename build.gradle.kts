plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("kapt") version "2.3.10"
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

val javalinVersion = "6.7.0"
val micrometerVersion = "1.15.5"
val flywayVersion = "11.15.0"
dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin:javalin-micrometer:$javalinVersion")
    implementation("org.eclipse.jetty:jetty-util")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")

    implementation("ch.qos.logback:logback-classic:1.5.20")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.zaxxer:HikariCP:6.2.1")

    implementation("org.apache.kafka:kafka-clients:4.1.0")

    implementation("no.nav.arbeid.pam:pam-styrk-yrkeskategori-mapper:1.20241030-dc26b440")

    // OpenApi
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion") // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion") // for Swagger UI
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:$javalinVersion") // for ReDoc UI

    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.3"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("net.bytebuddy:byte-buddy:1.17.8") // Må overstyre mockk sin bytebuddy for å støtte moderne java
}
