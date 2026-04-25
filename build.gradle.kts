plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	kotlin("plugin.jpa") version "2.2.21"
	kotlin("kapt") version "1.9.25"
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "my.reviewing"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:1.0.0-M6")
	}
	dependencies {
		dependency("io.swagger.core.v3:swagger-annotations-jakarta:2.2.30")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-batch")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.3")
	implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
	implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

	// Swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

	// Selenium
	implementation("org.seleniumhq.selenium:selenium-java:4.27.0")

	// Querydsl JPA
	implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")

	// APT(Annotation Processing Tool) - Kapt 사용
	kapt("com.querydsl:querydsl-apt:5.0.0:jakarta")
	kapt("jakarta.persistence:jakarta.persistence-api")
	kapt("jakarta.annotation:jakarta.annotation-api")

	implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter") {
		exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
	}
	implementation("org.springframework.ai:spring-ai-elasticsearch-store-spring-boot-starter") {
		exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
	}
	implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
