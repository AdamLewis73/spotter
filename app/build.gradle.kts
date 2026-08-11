// :app — Compose UI, ViewModels, navigation, and later CameraX and ML Kit.
// The only module allowed to touch the Android framework freely.

/**
 * Copies the built dictionary into the app's assets (D-10).
 *
 * A plain `Copy` task would be wrong here: with a missing source it is skipped
 * as NO-SOURCE, silently producing an app with no dictionary. This fails loudly
 * instead, which is the whole point — a missing or stale dictionary yields an
 * app that looks completely fine and serves wrong data.
 *
 * `builderSources` is not used for the copy. It exists so the task can refuse to
 * ship a database older than the code that generates it.
 *
 * The check is a timestamp comparison, and timestamps lie. `git checkout`, a
 * branch switch, or restoring a file all reset mtime without changing content,
 * which makes a perfectly current database look stale. That false positive is
 * why `-PallowStaleDictionary=true` exists: it downgrades the failure to a
 * warning for exactly those cases, and costs a flag instead of a 45-second
 * rebuild. A guard that cries wolf gets deleted, so it needs an escape hatch.
 *
 * The real guarantee is elsewhere. CI builds the dictionary from the committed
 * sources on every push and assembles the APK from *that*, so a stale asset
 * cannot reach master no matter what any local working copy looks like. This
 * task is a fast local warning, not the last line of defence.
 *
 * A fresh clone has no database at all (it is gitignored), so that case is
 * caught by the presence check — which has no escape hatch, because there is
 * nothing to ship at all.
 */
abstract class StageDictionaryAsset : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dictionary: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val builderSources: ConfigurableFileCollection

    @get:Input
    abstract val assetName: Property<String>

    /** `-PallowStaleDictionary=true` — see the class docs for why this exists. */
    @get:Input
    abstract val allowStale: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stage() {
        val db = dictionary.files.firstOrNull()
        if (db == null || !db.exists()) {
            throw GradleException(
                """
                The dictionary has not been built, so there is nothing to ship.

                  cd tools/dictbuild
                  python build.py        # ~45 s, no network needed (D-55)

                The sources are committed, so no fetch step is required. The
                output is gitignored, which is why a fresh clone never has one.
                """.trimIndent(),
            )
        }

        val newestBuilderSource = builderSources.files
            .filter { it.isFile }
            .maxByOrNull { it.lastModified() }

        if (newestBuilderSource != null && newestBuilderSource.lastModified() > db.lastModified()) {
            val message =
                """
                The dictionary is older than the code that builds it, so the app
                may ship stale data while looking perfectly healthy.

                  ${newestBuilderSource.name} is newer than ${db.name}.

                Rebuild it:

                  cd tools/dictbuild
                  python build.py

                If ${newestBuilderSource.name} did not actually change — a branch
                switch or `git checkout` resets mtime without touching content —
                re-run with -PallowStaleDictionary=true to proceed anyway.
                """.trimIndent()

            if (allowStale.get()) {
                logger.warn("WARNING: $message")
            } else {
                throw GradleException(message)
            }
        }

        val destination = outputDir.get().asFile
        destination.mkdirs()
        db.copyTo(destination.resolve(assetName.get()), overwrite = true)
        logger.lifecycle("Staged ${db.name} (${db.length() / 1_048_576} MB) into assets")
    }
}

plugins {
    alias(libs.plugins.android.application)
    // Still required with AGP 9 even though Kotlin itself is now built in:
    // this plugin supplies the Compose compiler.
    alias(libs.plugins.compose.compiler)
}

// The dictionary is built by tools/dictbuild (desktop Python, D-10) and is
// gitignored — a build output, not a source file.
val dictionaryAssetName = "spotter.db"

val stageDictionaryAsset = tasks.register<StageDictionaryAsset>("stageDictionaryAsset") {
    group = "build"
    description = "Copies the built dictionary into the app's assets, failing if it is missing or stale."

    dictionary.from(rootProject.layout.projectDirectory.file("tools/dictbuild/data/build/$dictionaryAssetName"))
    // Only files that can actually change the database's CONTENTS. Deliberately
    // an exclude-list rather than an include-list: a new ingest stage added
    // later is then covered automatically, and the cost of being too broad is a
    // spurious rebuild prompt, while the cost of being too narrow is shipping
    // stale data silently — which is the entire thing this guard exists to stop.
    //
    // The excluded four cannot affect the output: verify.py and
    // test_dictbuild.py read it, inspect_sources.py reads the raw sources, and
    // fetch.py's effect shows up as a changed sources.lock.json, which IS
    // included. Editing the verifier used to fail the Android build, which is a
    // false alarm of exactly the kind that gets a guard switched off.
    builderSources.from(
        rootProject.layout.projectDirectory.dir("tools/dictbuild").asFileTree.matching {
            include("*.py", "schema.sql", "sources.json", "sources.lock.json")
            exclude("verify.py", "test_*.py", "inspect_sources.py", "fetch.py")
        },
    )
    assetName.set(dictionaryAssetName)
    allowStale.set(
        providers.gradleProperty("allowStaleDictionary").map(String::toBoolean).orElse(false),
    )
    outputDir.set(layout.buildDirectory.dir("generated/dictionaryAssets"))
}

android {
    namespace = "com.spotterkanji.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.spotterkanji.app" // permanent once published (D-63)
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Attach the staged dictionary as a generated asset directory.
//
// NOT `sourceSets.main.assets.srcDir(...)`: AGP 9 rejects a Provider there
// outright, because a source set is meant for static, hand-written files and
// wiring a task output through it loses the task dependency. The Variant API
// is the supported route for generated content, and it carries the dependency
// automatically — no `preBuild.dependsOn` needed.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            stageDictionaryAsset,
            StageDictionaryAsset::outputDir,
        )
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
}
