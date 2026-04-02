plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.redis"
version = "0.0.1-SNAPSHOT"
description = "redis-search-demo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // jackson object mapper
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")

    //jedis
    implementation("redis.clients:jedis:7.4.0")

    implementation("com.redis.om:redis-om-spring:2.0.4")
    annotationProcessor("com.redis.om:redis-om-spring:2.0.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
