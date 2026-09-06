plugins {
    id("com.android.application")
}

android {
    namespace = "dev.hanenashi.ytmnt"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hanenashi.ytmnt"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
}

val generatedYtmntAssets = layout.buildDirectory.dir("generated/ytmnt-assets")

val syncYtmntScript by tasks.registering(Copy::class) {
    from(rootProject.file("../ytmnt.user.js"))
    into(generatedYtmntAssets)
}

android.sourceSets.getByName("main").assets.srcDir(generatedYtmntAssets)

tasks.named("preBuild").configure {
    dependsOn(syncYtmntScript)
}

dependencies {
    implementation("androidx.webkit:webkit:1.17.0")
}
