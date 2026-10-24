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

# Version detection must ignore JVM warnings and reject unreadable versions.
check_mock_java() (
    fixture_specification=$1
    java() {
        echo '[0.001s][warning][os,container] Example JVM startup warning'
        if [[ "$fixture_specification" == failed ]]; then
            return 1
        fi
        printf '    java.specification.version = %s\n' "$fixture_specification"
    }
    check_java
)
check_mock_java 25 || fail "rejected Java 25 after a JVM warning"
if check_mock_java 21; then
    fail "accepted Java 21 after a JVM warning"
fi
if check_mock_java unknown; then
    fail "accepted an unreadable Java version"
fi
if check_mock_java failed; then
    fail "accepted a failed Java command"
fi

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

# A fresh extracted release has only its packaged configuration template.
RELEASE_MODE=true
mkdir -p "$RELEASE_DIR/config" "$RELEASE_DIR/lib"
cp "$SCRIPT_DIR/../src/main/resources/application.yml.template" "$RELEASE_DIR/config/"
cp "$test_dir/source-expected/"*.jar "$RELEASE_DIR/lib/"
INSTALL_DIR="$test_dir/fresh-install"
install_files
setup_config
expected_url="url: jdbc:h2:file:$INSTALL_DIR/data/studysync;DB_CLOSE_DELAY=-1;CACHE_SIZE=65536"
grep -Fq "$expected_url" "$INSTALL_DIR/config/application.yml" \
    || fail "did not configure the packaged database path"
[[ -d "$INSTALL_DIR/data" ]] || fail "did not create the data directory"

# Reinstalling preserves customized settings in the existing configuration.
printf '\n# custom setting\n' >> "$INSTALL_DIR/config/application.yml"
cp "$INSTALL_DIR/config/application.yml" "$test_dir/expected-config.yml"
install_files
setup_config
diff "$test_dir/expected-config.yml" "$INSTALL_DIR/config/application.yml"

# Report an incomplete package explicitly, before rewriting a missing file.
rm "$INSTALL_DIR/config/application.yml" "$INSTALL_DIR/config/application.yml.template"
if setup_config; then
    fail "accepted a package without a configuration template"
fi

echo 'Installer regression checks passed.'
