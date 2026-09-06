plugins {
    id("com.android.application")
}

android {
    namespace = "dev.hanenashi.ytmnt.gecko"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hanenashi.ytmnt.gecko"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-gecko"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += setOf("ChromeOsAbiSupport", "OldTargetApi")
    }
}

val generatedExtensionAssets = layout.buildDirectory.dir("generated/ytmnt-extension")

val syncYtmntExtension by tasks.registering(Copy::class) {
    from(rootProject.file("../ytmnt.user.js")) {
        rename { "content.js" }
    }
    into(generatedExtensionAssets.map { it.dir("web_extensions/ytmnt") })
}

android.sourceSets.getByName("main").assets.directories.add(
    generatedExtensionAssets.get().asFile.absolutePath
)

tasks.named("preBuild").configure {
    dependsOn(syncYtmntExtension)
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:155.0.20260903215306")
}
