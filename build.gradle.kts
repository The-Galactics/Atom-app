import java.util.Properties

plugins {
    id("com.android.application") version "8.7.3"
    id("com.google.protobuf") version "0.9.4"
}

// Cargar local.properties para leer variables de entorno
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.atom.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atom.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            val grpcHost = localProperties.getProperty("GRPC_HOST") ?: "10.0.2.2"
            val grpcPort = localProperties.getProperty("GRPC_PORT") ?: "50051"
            buildConfigField("String", "GRPC_HOST", "\"$grpcHost\"")
            buildConfigField("int", "GRPC_PORT", "$grpcPort")

            val picovoiceKey = localProperties.getProperty("PICOVOICE_ACCESS_KEY") ?: ""
            buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceKey\"")
        }
        debug {
            val grpcHost = localProperties.getProperty("GRPC_HOST") ?: "10.0.2.2"
            val grpcPort = localProperties.getProperty("GRPC_PORT") ?: "50051"
            buildConfigField("String", "GRPC_HOST", "\"$grpcHost\"")
            buildConfigField("int", "GRPC_PORT", "$grpcPort")

            val picovoiceKey = localProperties.getProperty("PICOVOICE_ACCESS_KEY") ?: ""
            buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceKey\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        // Tests use JUnit 5 (Jupiter); AGP runs them on the JUnit Platform.
        unitTests.all { it.useJUnitPlatform() }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
            task.plugins {
                create("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.10.0")

    // Testing (JUnit 5 + Mockito + AssertJ; grpc-testing pinned to the gRPC version below).
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:launcher")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("io.grpc:grpc-testing:1.62.2")
    testImplementation("io.grpc:grpc-inprocess:1.62.2") // InProcess{Server,Channel}Builder
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // Wake-word detection ("Hey Atom") — Porcupine on-device hotword engine.
    implementation("ai.picovoice:porcupine-android:3.0.3")

    // gRPC
    implementation("io.grpc:grpc-okhttp:1.62.2")
    implementation("io.grpc:grpc-protobuf-lite:1.62.2")
    implementation("io.grpc:grpc-stub:1.62.2")
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}
