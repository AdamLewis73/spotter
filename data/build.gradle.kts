// :data — Room, repository implementations, dictionary access, the Kuromoji
// Tokenizer implementation. Depends on :domain only.
//
// This IS an Android library, unlike :domain, because Room brings Android in.
// That compromise is accepted in `architecture.md`, and it is why the
// no-`android.*` rule here needs a CI grep rather than the compiler (D-60).
// Keep Android types confined to the Room and file-system plumbing; anything
// resembling business logic belongs in :domain.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.spotterkanji.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    testImplementation(libs.junit)
}
