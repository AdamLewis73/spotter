// Imported rather than fully qualified: inside a build script `java` resolves to
// Gradle's own `java` extension, so `java.security.MessageDigest` fails with
// "Unresolved reference 'security'".
import java.security.MessageDigest

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
 * It also refuses to ship a database older than the code that generates it.
 *
 * **That check compares content hashes, not timestamps** (D-65). `build.py`
 * publishes a hash per builder file in `build-info.json`; this re-hashes those
 * exact files and compares. Two consequences:
 *
 *  - No false positives. The previous version compared mtimes, and `git
 *    checkout` or a branch switch resets those without changing content — it
 *    fired on a `build.py` byte-identical to git and demanded a pointless
 *    45-second rebuild. `-PallowStaleDictionary=true` existed purely to escape
 *    that, and is gone with the flaw that motivated it.
 *  - One definition of "the builder". `build.py` owns the list; this task
 *    consumes it. The two cannot drift.
 *
 * Hashes are compared with line endings normalised, because git hands out CRLF
 * on Windows and LF on Linux for the same commit.
 *
 * The declared inputs are deliberately broader than the builder set — they only
 * decide when Gradle re-runs the task, while `build-info.json` decides the
 * verdict. Over-inclusion therefore costs an extra task execution and cannot
 * cause a spurious failure.
 *
 * The real guarantee remains CI, which rebuilds the dictionary from committed
 * sources on every push and assembles the APK from that.
 *
 * A fresh clone has no database at all (it is gitignored), so that case is
 * caught by the presence check.
 */
abstract class StageDictionaryAsset : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dictionary: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val builderSources: ConfigurableFileCollection

    /** `build-info.json`, which publishes a hash per builder file (D-65). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val buildInfo: ConfigurableFileCollection

    /** Where the builder files live, for re-hashing what `buildInfo` names. */
    @get:Internal
    abstract val builderDir: DirectoryProperty

    @get:Input
    abstract val assetName: Property<String>

    /** Tiny sidecar asset holding just the build id, for the staleness check. */
    @get:Input
    abstract val buildIdAssetName: Property<String>

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

        val info = buildInfo.files.firstOrNull()
        if (info == null || !info.exists()) {
            throw GradleException(
                "${db.name} exists but build-info.json does not, so the build " +
                    "that produced it cannot be identified. Rebuild with " +
                    "`python build.py` in tools/dictbuild.",
            )
        }

        // build-info.json is the single definition of "the builder" (D-65);
        // this only re-hashes what it names. Parsed with a small regex rather
        // than by adding a JSON library to the build classpath — the shape is
        // fixed and written by build.py.
        val builderSection = Regex("\"builder\"\\s*:\\s*\\{([^}]*)}")
            .find(info.readText())?.groupValues?.get(1)
            ?: throw GradleException("build-info.json has no \"builder\" section — rebuild the dictionary.")

        val recorded = Regex("\"([^\"]+)\"\\s*:\\s*\"([0-9a-f]+)\"")
            .findAll(builderSection)
            .associate { it.groupValues[1] to it.groupValues[2] }

        val dir = builderDir.get().asFile
        val changed = recorded.filter { (name, expected) ->
            val file = dir.resolve(name)
            !file.exists() || sha256Normalised(file) != expected
        }.keys

        if (changed.isNotEmpty()) {
            throw GradleException(
                """
                The dictionary was built from different code than is on disk now,
                so the app would ship stale data while looking perfectly healthy.

                  changed since the last build: ${changed.sorted().joinToString(", ")}

                Rebuild it:

                  cd tools/dictbuild
                  python build.py
                """.trimIndent(),
            )
        }

        val buildId = Regex("\"build_id\"\\s*:\\s*\"([0-9a-f]+)\"")
            .find(info.readText())?.groupValues?.get(1)
            ?: throw GradleException("build-info.json has no build_id — rebuild the dictionary.")

        val destination = outputDir.get().asFile
        destination.mkdirs()
        db.copyTo(destination.resolve(assetName.get()), overwrite = true)

        // Ships the dictionary's identity beside it, so the app can tell whether
        // the copy it extracted earlier came from this same build (D-65). Room
        // copies an asset out exactly once and never looks again, so without
        // this a rebuilt dictionary never reaches an installed app.
        destination.resolve(buildIdAssetName.get()).writeText(buildId)

        logger.lifecycle(
            "Staged ${db.name} (${db.length() / 1_048_576} MB) into assets, build $buildId",
        )
    }

    /**
     * Must match `builder_digests()` in build.py exactly, including the CRLF
     * normalisation — git hands out CRLF on Windows and LF on Linux for the
     * same commit, so hashing raw bytes would make this platform-dependent.
     */
    private fun sha256Normalised(file: File): String {
        val normalised = file.readBytes()
            .toString(Charsets.ISO_8859_1)
            .replace("\r\n", "\n")
            .toByteArray(Charsets.ISO_8859_1)
        return MessageDigest.getInstance("SHA-256")
            .digest(normalised)
            .joinToString("") { byte -> "%02x".format(byte) }
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

// Must match DictionaryDatabase.BUILD_ID_ASSET_NAME.
val dictionaryBuildIdAssetName = "spotter.db.build-id"

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
    buildInfo.from(rootProject.layout.projectDirectory.file("tools/dictbuild/data/build/build-info.json"))
    builderDir.set(rootProject.layout.projectDirectory.dir("tools/dictbuild"))
    assetName.set(dictionaryAssetName)
    buildIdAssetName.set(dictionaryBuildIdAssetName)
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

    packaging {
        resources {
            // kuromoji-ipadic and kuromoji-core each ship these, and the merger
            // refuses to guess which copy wins:
            //   2 files found with path 'META-INF/CONTRIBUTORS.md'
            //
            // These are build metadata, not the licence notice. Kuromoji is
            // Apache 2.0 and JMdict and friends are CC BY-SA, and every one of
            // those obligations is met by the in-app attribution screen
            // (`attribution.md`) — which is where a user can actually read it,
            // rather than buried in an archive they will never open.
            excludes += "/META-INF/CONTRIBUTORS.md"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Both jars carry an identical Apache 2.0 text; keeping one copy is
            // the point of pickFirst rather than exclude. The notice still
            // ships — see the comment above.
            pickFirsts += "/META-INF/LICENSE.md"
            pickFirsts += "/META-INF/NOTICE.md"
        }
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    // Supplies AndroidJUnitRunner itself, named in testInstrumentationRunner
    // above. Without it the install succeeds and the run dies at startup with
    // ClassNotFoundException, which reads like a packaging fault rather than a
    // missing dependency.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
}
