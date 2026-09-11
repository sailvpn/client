plugins {
	java
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.illiad"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot Core Starter
	implementation("org.springframework.boot:spring-boot-starter")

	// Netty Core Pipelines (Used for client channel handling)
	implementation("io.netty:netty-all")
	implementation("io.projectreactor.netty:reactor-netty:1.1.0")

	// Jackson for JSON serialization
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

	// JWT support for out-of-band auth handshakes
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")

	// Developer Utilities
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// Testing Suite
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ==============================================================================
// RUNTIME ENVIRONMENT TUNING (Cleaned and optimized for pure Java execution)
// ==============================================================================
tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	// Disable class sharing warning overlays during rapid unit testing execution loops
	jvmArgs = (jvmArgs ?: listOf()) + listOf("-Xshare:off")
}
