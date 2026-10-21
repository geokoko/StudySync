#!/bin/bash
# Exercise file installation in a disposable directory, without desktop integration.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/install.sh"

test_dir=$(mktemp -d)
trap 'rm -rf -- "$test_dir"' EXIT
INSTALL_DIR="$test_dir/installed"
BIN_DIR="$test_dir/bin"
APPLICATIONS_DIR="$test_dir/applications"
ICONS_DIR="$test_dir/icons"
RELEASE_DIR="$test_dir/release"
PROJECT_ROOT="$test_dir/source"

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/data" "$INSTALL_DIR/config/google" \
    "$INSTALL_DIR/tokens" "$RELEASE_DIR/lib"
printf 'old application' > "$INSTALL_DIR/lib/StudySync-0.1.7-plain.jar"
printf 'old Spring' > "$INSTALL_DIR/lib/spring-core-6.1.1.jar"
printf 'old JavaFX' > "$INSTALL_DIR/lib/javafx-controls-21-linux.jar"
printf 'removed dependency' > "$INSTALL_DIR/lib/obsolete.jar"
printf 'study data' > "$INSTALL_DIR/data/studysync.mv.db"
printf 'custom settings' > "$INSTALL_DIR/config/application.yml"
printf 'OAuth settings' > "$INSTALL_DIR/config/google/drive.properties"
printf 'OAuth token' > "$INSTALL_DIR/tokens/StoredCredential"
cp -r "$INSTALL_DIR" "$test_dir/before"

assert_user_files_unchanged() {
    for directory in data config tokens; do
        diff -r "$test_dir/before/$directory" "$INSTALL_DIR/$directory"
    done
}

# A packaged upgrade must contain exactly the new distribution's libraries.
RELEASE_MODE=true
printf 'new application' > "$RELEASE_DIR/lib/studysync-0.1.8-plain.jar"
printf 'new Spring' > "$RELEASE_DIR/lib/spring-core-6.2.19.jar"
printf 'new JavaFX' > "$RELEASE_DIR/lib/javafx-controls-25.0.5-linux.jar"
install_files
diff -r "$RELEASE_DIR/lib" "$INSTALL_DIR/lib"
assert_user_files_unchanged
install_files
diff -r "$RELEASE_DIR/lib" "$INSTALL_DIR/lib"

# An incomplete release must leave the previous runtime in place.
cp -r "$INSTALL_DIR/lib" "$test_dir/expected-lib"
rm "$RELEASE_DIR/lib/studysync-0.1.8-plain.jar"
if install_files; then
    fail "accepted a release without an application jar"
fi
diff -r "$test_dir/expected-lib" "$INSTALL_DIR/lib"
assert_user_files_unchanged

# Copy failures also leave the previous runtime intact.
mv "$RELEASE_DIR/lib" "$RELEASE_DIR/unavailable-lib"
if install_files; then
    fail "accepted unreadable release libraries"
fi
diff -r "$test_dir/expected-lib" "$INSTALL_DIR/lib"

# A source install can use just the executable Boot jar, without retained dependencies.
RELEASE_MODE=false
mkdir -p "$PROJECT_ROOT/build/libs" "$PROJECT_ROOT/src/main/resources"
printf 'executable application' > "$PROJECT_ROOT/build/libs/studysync-0.1.8.jar"
install_files
diff -r "$PROJECT_ROOT/build/libs" "$INSTALL_DIR/lib"
assert_user_files_unchanged

# Also cover source builds with an installDist dependency directory.
mkdir -p "$PROJECT_ROOT/build/install/studysync/lib"
printf 'current dependency' > "$PROJECT_ROOT/build/install/studysync/lib/spring-core-6.2.19.jar"
cp -r "$PROJECT_ROOT/build/libs" "$test_dir/source-expected"
cp "$PROJECT_ROOT/build/install/studysync/lib/"*.jar "$test_dir/source-expected/"
install_files
diff -r "$test_dir/source-expected" "$INSTALL_DIR/lib"
assert_user_files_unchanged

# Fresh installs take the same path, without needing any previous jars.
rm -r "$INSTALL_DIR/lib"
install_files
diff -r "$test_dir/source-expected" "$INSTALL_DIR/lib"
if compgen -G "$INSTALL_DIR/.lib.*" > /dev/null; then
    fail "left staged or previous libraries behind"
fi

echo 'Installer regression checks passed.'
