// :domain — models, use cases, FSRS, repository interfaces, the Tokenizer
// interface. Depends on nothing.
//
// This is a PLAIN KOTLIN/JVM MODULE, not an Android library, and that is
// deliberate (D-60). `android.jar` is not on this module's compile classpath,
// so `import android.os.Bundle` does not resolve — the no-`android.*` rule that
// makes a future iOS port feasible is enforced by the compiler rather than by
// anyone remembering to check.
//
// Second benefit: tests here are ordinary JUnit and run in milliseconds, with
// no emulator and no Robolectric.
//
// If something in here appears to need an Android type, that is the signal it
// belongs in :data or :app — not a reason to change this module's type.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Emit Java 17 bytecode, matching what :app and :data compile against. Android's
// dexer (d8/r8) rejects class files stamped newer than it understands, so every
// module the app consumes must target 17 regardless of which JDK compiles it.
//
// Deliberately NOT `jvmToolchain(17)`, which requires a JDK 17 to be installed
// or auto-provisioned. This targets 17 using whatever JDK runs Gradle — one
// fewer toolchain download, and one fewer thing to install on a fresh machine.
//
// `-Xjdk-release` is what makes that safe. Setting jvmTarget alone only stamps
// the OUTPUT as 17 while still compiling against the running JDK's standard
// library — so a call to a Java 21 API would compile, stamp itself 17, and then
// fail at runtime with NoSuchMethodError. This restricts the visible API surface
// to 17 as well, turning that into a compile error.
//
// Switch to a real toolchain if byte-for-byte reproducibility across machines
// ever matters more than setup cost.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    // javac's equivalent of the above, for any Java sources that appear later.
    options.release.set(17)
}

dependencies {
    testImplementation(libs.junit)
}
