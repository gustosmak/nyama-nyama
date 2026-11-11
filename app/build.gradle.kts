import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.DirtyTeam.sky.NyamaNyama"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.DirtyTeam.sky.NyamaNyama"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AGENT_HOST", "\"${agentLanHost()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val startLocalBackend = tasks.register("startLocalBackend") {
    doLast {
        val open = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 8765), 400)
            }
            true
        } catch (ignored: Exception) {
            false
        }
        val sdkLine = rootProject.projectDir.resolve("local.properties").takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }
        val sdkDir = sdkLine?.substringAfter("=")
            ?.replace("\\:", ":")
            ?.replace("\\\\", "\\")
        val adb: File? = if (sdkDir == null) null else File(sdkDir, "platform-tools/adb.exe")
        if (adb != null && adb.exists()) {
            try {
                ProcessBuilder(adb.absolutePath, "reverse", "tcp:8765", "tcp:8765")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            } catch (ignored: Exception) {
            }
        }
        if (open) {
            return@doLast
        }
        val root = rootProject.projectDir
        val venvPython = root.resolve(".venv/Scripts/python.exe")
        val python = if (venvPython.exists()) venvPython.absolutePath else "python"
        val script = root.resolve("local_backend/serve.py").absolutePath
        val work = root.resolve("local_backend").absolutePath
        val log = root.resolve("local_backend/backend.log").absolutePath
        try {
            ProcessBuilder(
                listOf(
                    "cmd.exe", "/c", "start", "NyamaBackend", "/min",
                    "cmd.exe", "/c",
                    "set PYTHONUNBUFFERED=1&& cd /d $work && $python $script >> $log 2>&1"
                )
            ).start()
        } catch (error: Exception) {
            println("Не удалось запустить агентов: ${error.message}")
        }
    }
}

gradle.projectsEvaluated {
    tasks.findByName("installDebug")?.finalizedBy(startLocalBackend)
}

fun agentLanHost(): String {
    return try {
        val address = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { network -> network.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
        if (address.isNullOrBlank()) "http://10.0.2.2:8765" else "http://$address:8765"
    } catch (ignored: Exception) {
        "http://10.0.2.2:8765"
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.core.splashscreen)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(libs.osmdroid.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}