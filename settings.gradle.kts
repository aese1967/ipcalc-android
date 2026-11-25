pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("SKIP_ANDROID") == null) google()
        mavenCentral()
    }
}
rootProject.name = "IpCalcRed"
include(":desktop")
if (System.getenv("SKIP_ANDROID") == null) include(":app")
