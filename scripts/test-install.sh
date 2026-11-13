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

# Run the complete installer flow in an isolated shell. Only the Java prerequisite
# and desktop cache integration are stubbed; installation and cleanup are real.
installer_source="$SCRIPT_DIR/install.sh"
template_source="$SCRIPT_DIR/../src/main/resources/application.yml.template"
uninstaller_source="$SCRIPT_DIR/uninstall.sh"
run_installer() {
    bash -c '
        source "$1"
        INSTALL_DIR="$2/installed"
        BIN_DIR="$2/bin"
        APPLICATIONS_DIR="$2/applications"
        ICONS_DIR="$2/icons"
        check_java() { :; }
        install_desktop_entry() { :; }
        main "${@:3}"
    ' bash "$@"
}

make_release_fixture() {
    local base=$1
    mkdir -p "$base/studysync-test-linux/lib" "$base/studysync-test-linux/config/google"
    cp "$installer_source" "$base/studysync-test-linux/install.sh"
    cp "$uninstaller_source" "$base/studysync-test-linux/uninstall.sh"
    cp "$template_source" "$base/studysync-test-linux/config/"
    printf 'OAuth template' > "$base/studysync-test-linux/config/google/drive.properties.template"
    printf 'application' > "$base/studysync-test-linux/lib/studysync-test.jar"
    printf 'icon' > "$base/studysync-test-linux/icon.png"
    printf 'readme' > "$base/studysync-test-linux/README.txt"
    printf 'desktop entry' > "$base/studysync-test-linux/studysync.desktop"
    (
        cd "$base"
        tar -czf studysync-test-linux.tar.gz studysync-test-linux
        cp studysync-test-linux.tar.gz studysync-linux.tar.gz
        sha256sum studysync-test-linux.tar.gz > studysync-test-linux.tar.gz.sha256
        sha256sum studysync-linux.tar.gz > studysync-linux.tar.gz.sha256
    )
}

# Default cleanup removes both download aliases and an untouched extracted package,
# including when invoked from that package in a path containing spaces.
release_test="$test_dir/release cleanup"
make_release_fixture "$release_test"
mkdir -p "$release_test/installed/data" "$release_test/installed/tokens" "$release_test/installed/config/google"
printf 'existing study data' > "$release_test/installed/data/studysync.mv.db"
printf 'existing OAuth token' > "$release_test/installed/tokens/StoredCredential"
printf 'existing OAuth settings' > "$release_test/installed/config/google/drive.properties"
cp -r "$release_test/installed" "$release_test/before"
(
    cd "$release_test/studysync-test-linux"
    run_installer "$PWD/install.sh" "$release_test"
)
[[ ! -e "$release_test/studysync-test-linux" ]] || fail "left the extracted package behind"
if compgen -G "$release_test/*.tar.gz*" > /dev/null; then
    fail "left matching downloads behind"
fi
[[ -f "$release_test/installed/lib/studysync-test.jar" ]] || fail "removed installed libraries"
[[ -x "$release_test/installed/bin/studysync" ]] || fail "lost the installed launcher"
[[ -x "$release_test/installed/bin/uninstall.sh" ]] || fail "cleaned before copying the uninstaller"
[[ -f "$release_test/installed/config/application.yml" ]] || fail "removed installed configuration"
for file in data/studysync.mv.db tokens/StoredCredential config/google/drive.properties; do
    cmp "$release_test/before/$file" "$release_test/installed/$file"
done

# The explicit opt-out retains archives and the entire extracted tree.
keep_test="$test_dir/keep"
make_release_fixture "$keep_test"
cp -r "$keep_test/studysync-test-linux" "$keep_test/expected"
run_installer "$keep_test/studysync-test-linux/install.sh" "$keep_test" --keep-install-files
diff -r "$keep_test/expected" "$keep_test/studysync-test-linux"
[[ -f "$keep_test/studysync-linux.tar.gz.sha256" ]] || fail "ignored the cleanup opt-out"

# A later installation failure must not delete the package or downloads.
failed_test="$test_dir/failed"
make_release_fixture "$failed_test"
rm "$failed_test/studysync-test-linux/config/application.yml.template"
cp -r "$failed_test/studysync-test-linux" "$failed_test/expected"
if run_installer "$failed_test/studysync-test-linux/install.sh" "$failed_test"; then
    fail "accepted an incomplete package during the full installation"
fi
diff -r "$failed_test/expected" "$failed_test/studysync-test-linux"
[[ -f "$failed_test/studysync-linux.tar.gz" ]] || fail "cleaned after installation failed"

# Extra files (including data and dotfiles) survive; unrelated archives/checksums
# must survive even when their filenames look like the installer downloads.
extra_test="$test_dir/extra"
make_release_fixture "$extra_test"
mkdir -p "$extra_test/studysync-test-linux/data" "$extra_test/studysync-test-linux/tokens"
printf 'study data' > "$extra_test/studysync-test-linux/data/studysync.mv.db"
printf 'token' > "$extra_test/studysync-test-linux/tokens/StoredCredential"
printf 'settings' > "$extra_test/studysync-test-linux/config/google/drive.properties"
printf 'notes' > "$extra_test/studysync-test-linux/.notes"
printf 'unrelated' > "$extra_test/other.txt"
tar -czf "$extra_test/studysync-linux.tar.gz" -C "$extra_test" other.txt
cp "$extra_test/studysync-linux.tar.gz" "$extra_test/expected-archive"
cp -r "$extra_test/studysync-test-linux" "$extra_test/expected"
run_installer "$extra_test/studysync-test-linux/install.sh" "$extra_test"
for file in data/studysync.mv.db tokens/StoredCredential config/google/drive.properties .notes; do
    cmp "$extra_test/expected/$file" "$extra_test/studysync-test-linux/$file"
done
cmp "$extra_test/expected-archive" "$extra_test/studysync-linux.tar.gz"

checksum_test="$test_dir/checksum"
make_release_fixture "$checksum_test"
printf 'custom checksum' > "$checksum_test/studysync-linux.tar.gz.sha256"
run_installer "$checksum_test/studysync-test-linux/install.sh" "$checksum_test"
[[ -f "$checksum_test/studysync-linux.tar.gz.sha256" ]] || fail "removed an unrelated checksum"
[[ ! -e "$checksum_test/studysync-linux.tar.gz" ]] || fail "retained a matching archive"

# Cleanup never follows a package-directory symlink or touches the live install.
(
    RELEASE_MODE=true
    RELEASE_DIR="$extra_test/installed"
    INSTALL_DIR="$extra_test/installed"
    cleanup_installation
    [[ -f "$INSTALL_DIR/lib/studysync-test.jar" ]] || fail "cleaned the live installation"
    RELEASE_DIR="$extra_test"
    cleanup_installation
    [[ -f "$INSTALL_DIR/lib/studysync-test.jar" ]] || fail "cleaned an ancestor of the installation"
    RELEASE_DIR="$extra_test/symlink-release"
    mkdir -p "$RELEASE_DIR"
    ln -s "$INSTALL_DIR/lib" "$RELEASE_DIR/lib"
    cleanup_installation
    [[ -f "$INSTALL_DIR/lib/studysync-test.jar" ]] || fail "followed a package directory symlink"
)

# A source installation cleans generated output only after every installed file
# is ready. The fake Gradle wrapper avoids compiling Java for shell regressions.
source_test="$test_dir/source cleanup"
mkdir -p "$source_test/source/scripts" "$source_test/source/src/main/resources"
cp "$installer_source" "$uninstaller_source" "$source_test/source/scripts/"
cp "$template_source" "$source_test/source/src/main/resources/"
cat > "$source_test/source/gradlew" << 'GRADLE'
#!/bin/bash
set -e
if [[ "$*" == 'clean --quiet' ]]; then
    [[ -x ../installed/bin/studysync && -x ../installed/bin/uninstall.sh ]]
    [[ -f ../installed/config/application.yml ]]
    rm -rf -- build
else
    mkdir -p build/libs .gradle
    printf 'application' > build/libs/studysync-test.jar
    printf 'cache' > .gradle/cache
fi
GRADLE
chmod +x "$source_test/source/gradlew"
run_installer "$source_test/source/scripts/install.sh" "$source_test" --build
[[ ! -e "$source_test/source/build" ]] || fail "left source build output behind"
[[ -f "$source_test/source/.gradle/cache" ]] || fail "removed the source build cache"
[[ -f "$source_test/source/scripts/install.sh" ]] || fail "removed the source installer"
run_installer "$source_test/source/scripts/install.sh" "$source_test" --keep-install-files
[[ -f "$source_test/source/build/libs/studysync-test.jar" ]] || fail "ignored the source cleanup opt-out"

# Cleanup errors warn but do not turn a successful install into a failure.
sed -i '/rm -rf -- build/c\    exit 1' "$source_test/source/gradlew"
run_installer "$source_test/source/scripts/install.sh" "$source_test" > "$source_test/output"
grep -q 'build output could not be cleaned' "$source_test/output" || fail "did not report cleanup failure"

echo 'Installer regression checks passed.'
