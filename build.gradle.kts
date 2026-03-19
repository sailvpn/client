plugins {
	java
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.osdetector") version "1.7.3"
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
	implementation("org.springframework.boot:spring-boot-starter")
	// https://mvnrepository.com/artifact/io.netty/netty-all
	implementation("io.netty:netty-all")
	implementation("io.projectreactor.netty:reactor-netty:1.1.0")
	// Jackson for JSON serialization
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
	// JWT support
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")
	// Source: https://mvnrepository.com/artifact/io.netty/netty-tcnative-boringssl-static
	implementation("io.netty:netty-tcnative-boringssl-static:2.0.74.Final:${osdetector.classifier}")
	// Ensure Java wrapper classes for tcnative are available
	implementation("io.netty:netty-tcnative:2.0.74.Final")
	implementation("io.netty:netty-tcnative-classes:2.0.74.Final")
	// https://mvnrepository.com/artifact/org.projectlombok/lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.test {
	// OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
	//kotlin
	jvmArgs = listOf("-Xshare:off")
	//groovy
	// jvmArgs "-Xshare:off";
}

// Configure test framework, adopting Netty native workdir to a user-writable location
// this is needed to avoid issues when running tests in environments where /tmp is not writable
// the purpose is to accodomate io.netty:netty-tcnative-boringssl-static:2.0.74.Final
val nettyNativeWorkdir = System.getProperty("user.home") + "/.netty-native"

tasks.withType<Test> {
	useJUnitPlatform()
	// ensure tests can extract native libs reliably
	jvmArgs = (jvmArgs ?: listOf()) + listOf("-Dio.netty.native.workdir=$nettyNativeWorkdir", "-Djava.io.tmpdir=$nettyNativeWorkdir")
}

// Configure bootRun to pass the same JVM args so running via Gradle works
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	// append to existing jvmArgs
	jvmArgs = (jvmArgs ?: listOf()) + listOf("-Dio.netty.native.workdir=$nettyNativeWorkdir", "-Djava.io.tmpdir=$nettyNativeWorkdir")
}