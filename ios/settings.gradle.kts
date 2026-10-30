// نسخة iPad: بناء Gradle مستقل عن تطبيق أندرويد (المجلد الأعلى)، يُخرج إطار Kotlin يحتضنه تطبيق Xcode في iosApp/
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MyNotesIOS"
include(":shared")
