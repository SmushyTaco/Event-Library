import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
	`maven-publish`
	signing
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.dokka)
	alias(libs.plugins.yumiGradleLicenser)
	alias(libs.plugins.dotenv)
	alias(libs.plugins.nmcp)
}

val projectName = providers.gradleProperty("name")
val projectGroup = providers.gradleProperty("group")
val projectVersion = providers.gradleProperty("version")
val projectDescription = providers.gradleProperty("description")

val publishingUrl = providers.gradleProperty("url")

val licenseName = providers.gradleProperty("license_name")
val licenseUrl = providers.gradleProperty("license_url")
val licenseDistribution = providers.gradleProperty("license_distribution")

val developerId = providers.gradleProperty("developer_id")
val developerName = providers.gradleProperty("developer_name")
val developerEmail = providers.gradleProperty("developer_email")

val publishingStrategy = providers.gradleProperty("publishing_strategy")

val javaVersion = libs.versions.java.map { it.toInt() }

base.archivesName = projectName
group = projectGroup.get()
version = projectVersion.get()
description = projectDescription.get()

repositories { mavenCentral() }

dependencies {
	implementation(libs.slf4j.api)
	implementation(libs.caffeine)
	testRuntimeOnly(libs.slf4j.simple)
	testImplementation(kotlin("test"))
	testImplementation(libs.junit.jupiter)
}
java {
	toolchain {
		languageVersion = javaVersion.map { JavaLanguageVersion.of(it) }
		vendor = JvmVendorSpec.ADOPTIUM
	}
	sourceCompatibility = JavaVersion.toVersion(javaVersion.get())
	targetCompatibility = JavaVersion.toVersion(javaVersion.get())
	withSourcesJar()
}
dokka {
	dokkaSourceSets.configureEach {
		documentedVisibilities.addAll(VisibilityModifier.entries)
		reportUndocumented = true
	}
}
val licenseFile = run {
	val rootLicense = layout.projectDirectory.file("LICENSE")
	val parentLicense = layout.projectDirectory.file("../LICENSE")
	when {
		rootLicense.asFile.exists() -> {
			logger.lifecycle("Using LICENSE from project root: {}", rootLicense.asFile)
			rootLicense
		}
		parentLicense.asFile.exists() -> {
			logger.lifecycle("Using LICENSE from parent directory: {}", parentLicense.asFile)
			parentLicense
		}
		else -> {
			logger.warn("No LICENSE file found in project or parent directory.")
			null
		}
	}
}
tasks {
	withType<JavaCompile>().configureEach {
		options.encoding = "UTF-8"
		sourceCompatibility = javaVersion.get().toString()
		targetCompatibility = javaVersion.get().toString()
		if (javaVersion.get() > 8) options.release = javaVersion
	}
	named<UpdateDaemonJvm>("updateDaemonJvm") {
		languageVersion = libs.versions.gradleJava.map { JavaLanguageVersion.of(it.toInt()) }
		vendor = JvmVendorSpec.ADOPTIUM
	}
	withType<JavaExec>().configureEach { defaultCharacterEncoding = "UTF-8" }
	withType<Javadoc>().configureEach { options.encoding = "UTF-8" }
	withType<Test>().configureEach {
		defaultCharacterEncoding = "UTF-8"
		useJUnitPlatform()
	}
	register<Jar>("dokkaJar") {
		group = JavaBasePlugin.DOCUMENTATION_GROUP
		dependsOn(dokkaGenerateHtml)
		archiveClassifier = "javadoc"
		from(layout.buildDirectory.dir("dokka/html"))
	}
	named("build") { dependsOn(named("dokkaJar")) }
	withType<KotlinCompile>().configureEach {
		compilerOptions {
			extraWarnings = true
			jvmTarget = javaVersion.map { JvmTarget.valueOf("JVM_${if (it == 8) "1_8" else it}") }
		}
	}
	withType<Jar>().configureEach {
		licenseFile?.let {
			from(it) {
				rename { original -> "${original}_${archiveBaseName.get()}" }
			}
		}
	}
}
license {
	rule(file("./HEADER"))
	include("**/*.kt")
	exclude("**/*.properties")
}
publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			groupId = projectGroup.get()
			artifactId = projectName.get()
			version = projectVersion.get()
			artifact(tasks.named("dokkaJar"))
			pom {
				name = projectName
				description = projectDescription
				url = publishingUrl

				licenses {
					license {
						name = licenseName
						url = licenseUrl
						distribution = licenseDistribution
					}
				}
				developers {
					developer {
						id = developerId
						name = developerName
						email = developerEmail
					}
				}
				scm {
					url = publishingUrl
					connection = publishingUrl.map { "scm:git:$it.git" }
					developerConnection = publishingUrl.map { "scm:git:$it.git" }
				}
			}
		}
	}
}
signing {
	val keyFile = layout.projectDirectory.file("./private-key.asc")
	if (keyFile.asFile.exists()) {
		isRequired = true
		useInMemoryPgpKeys(
			providers.fileContents(keyFile).asText.get(),
			env.fetch("PASSPHRASE", "")
		)
		sign(publishing.publications)
	}
}
nmcp {
	publishAllPublicationsToCentralPortal {
		username = env.fetch("USERNAME_TOKEN", "")
		password = env.fetch("PASSWORD_TOKEN", "")
		publishingType = publishingStrategy
	}
}
