#!/bin/bash
#
# StudySync Release Builder
# Creates a distributable .tar.gz package for GitHub releases
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

resolve_version() {
    local version_value=""

    # 1) Prefer Gradle properties (handles Kotlin/Groovy DSL and formatting changes)
    if [[ -x "$PROJECT_ROOT/gradlew" ]]; then
        version_value=$(cd "$PROJECT_ROOT" && ./gradlew -q properties --property version 2>/dev/null | awk -F': ' '/^version:/ {print $2; exit}')
    fi

    # 2) Fallback to build.gradle line parsing (single/double quotes)
    if [[ -z "$version_value" && -f "$PROJECT_ROOT/build.gradle" ]]; then
        version_value=$(sed -nE "s/^[[:space:]]*version[[:space:]]*=[[:space:]]*['\"]([^'\"]+)['\"].*$/\1/p" "$PROJECT_ROOT/build.gradle" | head -1)
    fi

    # 3) Fallback to gradle.properties (version=...)
    if [[ -z "$version_value" && -f "$PROJECT_ROOT/gradle.properties" ]]; then
        version_value=$(sed -nE "s/^[[:space:]]*version[[:space:]]*=[[:space:]]*([^[:space:]]+).*$/\1/p" "$PROJECT_ROOT/gradle.properties" | head -1)
    fi

    # 4) Final safe default
    if [[ -z "$version_value" ]]; then
        version_value="0.1.0"
    fi

    echo "$version_value"
}

VERSION=$(resolve_version)

RELEASE_NAME="studysync-$VERSION-linux"
BUILD_DIR="$PROJECT_ROOT/build/release"
RELEASE_DIR="$BUILD_DIR/$RELEASE_NAME"

print_header() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║                StudySync Release Builder                  ║"
    echo "║                    Version: $VERSION                        ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_info() {
    echo -e "${BLUE}→ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

clean_previous() {
    print_info "Cleaning previous build..."
    rm -rf "$BUILD_DIR"
    mkdir -p "$RELEASE_DIR"
    print_success "Clean complete"
}

build_application() {
    print_info "Building application..."
    
    cd "$PROJECT_ROOT"
    
    # Build with Gradle - creates distribution
    ./gradlew clean build installDist -x test --quiet
    
    if [[ $? -ne 0 ]]; then
        print_error "Build failed!"
        exit 1
    fi
    
    print_success "Application built"
}

package_release() {
    print_info "Packaging release..."
    
    # Create directory structure
    mkdir -p "$RELEASE_DIR/lib"
    mkdir -p "$RELEASE_DIR/config/google"
    
    # Copy all JARs from installDist
    local install_lib_dir
    install_lib_dir=$(find "$PROJECT_ROOT/build/install" -mindepth 2 -maxdepth 2 -type d -name lib | head -1)
    if [[ -n "$install_lib_dir" && -d "$install_lib_dir" ]]; then
        cp "$install_lib_dir/"*.jar "$RELEASE_DIR/lib/"
    else
        # Fallback to just the main jar
        cp "$PROJECT_ROOT/build/libs/"*.jar "$RELEASE_DIR/lib/"
    fi
    
    # Copy scripts
    cp "$SCRIPT_DIR/install.sh" "$RELEASE_DIR/"
    cp "$SCRIPT_DIR/uninstall.sh" "$RELEASE_DIR/"
    chmod +x "$RELEASE_DIR/install.sh"
    chmod +x "$RELEASE_DIR/uninstall.sh"
    
    # Copy desktop file
    if [[ -f "$PROJECT_ROOT/packaging/studysync.desktop" ]]; then
        cp "$PROJECT_ROOT/packaging/studysync.desktop" "$RELEASE_DIR/"
    fi
    
    # Copy icon
    if [[ -f "$PROJECT_ROOT/src/main/resources/icon.png" ]]; then
        cp "$PROJECT_ROOT/src/main/resources/icon.png" "$RELEASE_DIR/"
    fi
    
    # Copy config templates
    if [[ -f "$PROJECT_ROOT/src/main/resources/application.yml.template" ]]; then
        cp "$PROJECT_ROOT/src/main/resources/application.yml.template" "$RELEASE_DIR/config/"
    fi
    if [[ -f "$PROJECT_ROOT/config/google/drive.properties.template" ]]; then
        cp "$PROJECT_ROOT/config/google/drive.properties.template" "$RELEASE_DIR/config/google/"
    fi
    
    # Create README for the release
    cat > "$RELEASE_DIR/README.txt" << EOF
StudySync $VERSION for Linux
=============================

A comprehensive Study Management System for academic productivity.

INSTALLATION
------------
1. Extract this archive
2. Run: ./install.sh
3. StudySync will appear in your application menu

REQUIREMENTS
------------
• Java 21 or later
• Linux with a desktop environment (KDE, GNOME, XFCE, etc.)

UNINSTALLATION
--------------
Run: ~/.local/share/studysync/bin/uninstall.sh

For more information, visit:
https://github.com/geokoko/StudySync
EOF
    
    print_success "Release packaged"
}

create_tarball() {
    print_info "Creating tarball..."
    
    cd "$BUILD_DIR"
    tar -czf "$RELEASE_NAME.tar.gz" "$RELEASE_NAME"
    
    # Create a version-less copy so the GitHub Releases "latest" URL works:
    #   .../releases/latest/download/studysync-linux.tar.gz
    cp "$RELEASE_NAME.tar.gz" "studysync-linux.tar.gz"
    
    # Also create SHA256 checksums
    sha256sum "$RELEASE_NAME.tar.gz" > "$RELEASE_NAME.tar.gz.sha256"
    sha256sum "studysync-linux.tar.gz" > "studysync-linux.tar.gz.sha256"
    
    print_success "Tarball created"
}

print_completion() {
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}            Release built successfully!                    ${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "Release files created in: $BUILD_DIR/"
    echo ""
    echo "  📦 $RELEASE_NAME.tar.gz"
    echo "  📦 studysync-linux.tar.gz (version-less alias)"
    echo "  🔐 $RELEASE_NAME.tar.gz.sha256"
    echo "  🔐 studysync-linux.tar.gz.sha256"
    echo ""
    echo "File size: $(du -h "$BUILD_DIR/$RELEASE_NAME.tar.gz" | cut -f1)"
    echo ""
    echo "Upload these files to GitHub Releases."
}

# Main build flow
main() {
    print_header
    
    # Parse arguments
    for arg in "$@"; do
        case $arg in
            --help|-h)
                echo "Usage: build-release.sh [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --help, -h     Show this help message"
                echo ""
                echo "Creates a distributable tarball for GitHub releases."
                exit 0
                ;;
        esac
    done
    
    clean_previous
    build_application
    package_release
    create_tarball
    print_completion
}

main "$@"
