val name = providers.gradleProperty("name")
rootProject.name = name.get()
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
	val foojayResolverVersion = providers.gradleProperty("foojay_resolver_version")
	plugins {
		id("org.gradle.toolchains.foojay-resolver-convention").version(foojayResolverVersion.get())
	}
}
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention")
}
