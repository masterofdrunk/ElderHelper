pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        // 华为云镜像 (可选)
        maven("https://mirrors.huaweicloud.com/repository/maven/")
        // 腾讯云镜像 (可选)
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")

        // 原始仓库作为备选 (重要)
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "elderhelper"
include(":app")