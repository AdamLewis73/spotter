# Release shrinking rules for :app.
#
# Empty on purpose: `isMinifyEnabled = false` in build.gradle.kts, so R8 does
# not run and nothing here is read. The file exists because that block names
# it, and a build that one day turns minification on should find it rather
# than a missing path.
#
# Before turning minification on, check Kuromoji first: it finds its bundled
# dictionary files relative to its own classes, which renaming can break.
# ML Kit and Room ship their own rules. Either way, test a release build on a
# device — a scan that works in debug and crashes in release is the failure
# to look for.
