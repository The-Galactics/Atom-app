plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.Atom.app"
version = "0.0.1-SNAPSHOT"
description = "Atom_app"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Copy>("installGitHooks") {
    description = "Install git hooks"

    from(file("${rootProject.projectDir}/scripts/commit-msg"))
    into(file("${rootProject.projectDir}/.git/hooks"))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    filePermissions {
        unix("rwxr-xr-x")
    }

    doFirst {
        println("Installing Git Hooks...")
    }
}

tasks.named("compileJava") {
    dependsOn("installGitHooks")
}