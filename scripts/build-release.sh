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

# Get version from build.gradle
VERSION=$(grep "^version = " "$PROJECT_ROOT/build.gradle" | sed "s/version = '\(.*\)'/\1/")
if [[ -z "$VERSION" ]]; then
    VERSION="0.1.0"
fi

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
    if [[ -d "$PROJECT_ROOT/build/install/StudySync/lib" ]]; then
        cp "$PROJECT_ROOT/build/install/StudySync/lib/"*.jar "$RELEASE_DIR/lib/"
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
    
    # Also create SHA256 checksum
    sha256sum "$RELEASE_NAME.tar.gz" > "$RELEASE_NAME.tar.gz.sha256"
    
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
    echo "  🔐 $RELEASE_NAME.tar.gz.sha256"
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
