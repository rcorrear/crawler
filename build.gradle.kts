plugins {
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    
    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core:1.3.2")
    
    // Database
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    
    // HTML parsing
    implementation("org.jsoup:jsoup:1.19.1")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test:1.3.2")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.6")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Spotless configuration - basic rules only for Java 25 compatibility
spotless {
    java {
        target("src/**/*.java")
        // No external formatter - just basic cleanup
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        leadingTabsToSpaces(4)
    }
}

// Wire spotlessCheck to compileJava
tasks.named("compileJava") {
    dependsOn("spotlessCheck")
}
