pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Tara"
include(":app", ":core:domain", ":core:audio", ":core:permissions", ":core:designsystem", ":platform:androidactions", ":data:settings", ":feature:assistant", ":feature:onboarding", ":feature:privacy", ":feature:settings")
