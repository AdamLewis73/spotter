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
    // Room generates its DAO implementations at compile time, which needs an
    // annotation processor. KSP is the current one; kapt is deprecated.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.spotterkanji.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export stays ON and the JSON is committed (D-18). It is
        // what makes a future migration reviewable rather than guessed at.
        // The dictionary itself is never migrated (D-38) — this matters for the
        // *user* database, which lands later in this phase.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))

    // Kuromoji is a plain JVM library, so it could technically live in :domain.
    // It belongs here because it is JVM-ONLY and cannot run on iOS — putting it
    // in :domain would quietly undo the portability D-60 protects. The
    // `Tokenizer` interface stays in :domain so this stays swappable (D-08).
    implementation(libs.kuromoji.ipadic)

    // `api`, not `implementation`: DictionaryDatabase extends RoomDatabase, so
    // Room types are genuinely part of this module's public surface and any
    // consumer needs them on its classpath to construct one. `implementation`
    // fails with "Cannot access androidx.room.RoomDatabase which is a supertype
    // of DictionaryDatabase", which is Gradle correctly reporting a leaked type.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
