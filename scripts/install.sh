#!/bin/bash
#
# StudySync Installer for Linux
# Installs StudySync to ~/.local/share/studysync with desktop integration
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Installation directories
INSTALL_DIR="$HOME/.local/share/studysync"
BIN_DIR="$HOME/.local/bin"
APPLICATIONS_DIR="$HOME/.local/share/applications"
ICONS_DIR="$HOME/.local/share/icons/hicolor/128x128/apps"

# Script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Check if we're running from extracted release or source
if [[ -d "$SCRIPT_DIR/lib" ]]; then
    # Running from extracted release tarball
    RELEASE_MODE=true
    RELEASE_DIR="$SCRIPT_DIR"
else
    # Running from source
    RELEASE_MODE=false
fi

print_header() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║                   StudySync Installer                     ║"
    echo "║         Study Management System for Linux                 ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}→ $1${NC}"
}

check_java() {
    print_info "Checking Java installation..."
    
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed!"
        echo "Please install Java 21 or later:"
        echo "  Ubuntu/Debian: sudo apt install openjdk-21-jdk"
        echo "  Fedora: sudo dnf install java-21-openjdk"
        echo "  Arch: sudo pacman -S jdk21-openjdk"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -lt 21 ]]; then
        print_error "Java 21 or later is required (found Java $JAVA_VERSION)"
        exit 1
    fi
    
    print_success "Java $JAVA_VERSION detected"
}

build_application() {
    print_info "Building StudySync..."
    
    if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
        print_error "Gradle wrapper not found. Are you in the StudySync directory?"
        exit 1
    fi
    
    cd "$PROJECT_ROOT"
    
    # Build the application
    ./gradlew clean build -x test --quiet
    
    if [[ $? -ne 0 ]]; then
        print_error "Build failed!"
        exit 1
    fi
    
    print_success "Build completed"
}

install_files() {
    print_info "Installing StudySync to $INSTALL_DIR..."
    
    # Create directories
    mkdir -p "$INSTALL_DIR/lib"
    mkdir -p "$INSTALL_DIR/bin"
    mkdir -p "$BIN_DIR"
    mkdir -p "$APPLICATIONS_DIR"
    mkdir -p "$ICONS_DIR"
    
    if [[ "$RELEASE_MODE" == true ]]; then
        # Copy from release tarball
        cp -r "$RELEASE_DIR/lib/"* "$INSTALL_DIR/lib/"
        
        # Copy config if exists
        if [[ -d "$RELEASE_DIR/config" ]]; then
            cp -r "$RELEASE_DIR/config" "$INSTALL_DIR/"
        fi
        
        # Copy icon
        if [[ -f "$RELEASE_DIR/icon.png" ]]; then
            cp "$RELEASE_DIR/icon.png" "$INSTALL_DIR/"
            cp "$RELEASE_DIR/icon.png" "$ICONS_DIR/studysync.png"
        fi
    else
        # Copy from build output
        cp "$PROJECT_ROOT/build/libs/"*.jar "$INSTALL_DIR/lib/"
        
        # Copy dependencies
        local install_lib_dir
        install_lib_dir=$(find "$PROJECT_ROOT/build/install" -mindepth 2 -maxdepth 2 -type d -name lib | head -1)
        if [[ -n "$install_lib_dir" && -d "$install_lib_dir" ]]; then
            cp "$install_lib_dir/"*.jar "$INSTALL_DIR/lib/" 2>/dev/null || true
        fi
        
        # Copy config
        if [[ -d "$PROJECT_ROOT/config" ]]; then
            cp -r "$PROJECT_ROOT/config" "$INSTALL_DIR/"
        fi
        
        # Copy resources
        mkdir -p "$INSTALL_DIR/resources"
        cp -r "$PROJECT_ROOT/src/main/resources/"* "$INSTALL_DIR/resources/" 2>/dev/null || true
        
        # Copy icon
        if [[ -f "$PROJECT_ROOT/src/main/resources/icon.png" ]]; then
            cp "$PROJECT_ROOT/src/main/resources/icon.png" "$INSTALL_DIR/"
            cp "$PROJECT_ROOT/src/main/resources/icon.png" "$ICONS_DIR/studysync.png"
        fi
    fi
    
    print_success "Files installed"
}

create_launcher() {
    print_info "Creating launcher script..."
    
    # Prefer executable jar when available; otherwise use classpath mode with plain jar
    local main_jar
    local launch_mode
    main_jar=$(find "$INSTALL_DIR/lib" \( -name "StudySync-*.jar" -o -name "studysync-*.jar" \) | grep -v "\-plain.jar" | head -1)

    if [[ -n "$main_jar" ]]; then
        launch_mode="jar"
    else
        main_jar=$(find "$INSTALL_DIR/lib" \( -name "StudySync-*-plain.jar" -o -name "studysync-*-plain.jar" \) | head -1)
        if [[ -z "$main_jar" ]]; then
            print_error "Application JAR not found in $INSTALL_DIR/lib"
            exit 1
        fi
        launch_mode="classpath"
    fi

    # Find Java executable
    JAVA_EXEC=$(which java)
    if [[ -z "$JAVA_EXEC" ]]; then
        JAVA_EXEC="java" # Fallback if which fails
    fi

    local launch_command
    if [[ "$launch_mode" == "jar" ]]; then
        launch_command="exec \"$JAVA_EXEC\" -jar \"$main_jar\" \"\$@\""
    else
        launch_command="exec \"$JAVA_EXEC\" -cp \"\$INSTALL_DIR/lib/*\" com.studysync.StudySyncApplication \"\$@\""
    fi
    
    # Create launcher script
    cat > "$INSTALL_DIR/bin/studysync" << LAUNCHER
#!/bin/bash
#
# StudySync Launcher
#

# Use absolute paths
INSTALL_DIR="\$HOME/.local/share/studysync"
cd "\$INSTALL_DIR"

# Run the application
$launch_command
LAUNCHER
    
    chmod +x "$INSTALL_DIR/bin/studysync"
    
    # Create symlink in user's bin directory
    ln -sf "$INSTALL_DIR/bin/studysync" "$BIN_DIR/studysync"
    
    print_success "Launcher created"
}

install_desktop_entry() {
    print_info "Installing desktop entry..."
    
    local desktop_source=""

    if [[ "$RELEASE_MODE" == true ]]; then
        if [[ -f "$RELEASE_DIR/studysync.desktop" ]]; then
             desktop_source="$RELEASE_DIR/studysync.desktop"
        fi
    else
        if [[ -f "$PROJECT_ROOT/packaging/studysync.desktop" ]]; then
             desktop_source="$PROJECT_ROOT/packaging/studysync.desktop"
        fi
    fi

    if [[ -z "$desktop_source" ]]; then
        print_warning "Desktop file template not found (checked packaging/studysync.desktop). Generating default..."
        
        # Fallback to generating it if source not found
        cat > "$APPLICATIONS_DIR/studysync.desktop" << EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=StudySync
GenericName=Study Manager
Comment=A comprehensive Study Management System for academic productivity
Exec=$INSTALL_DIR/bin/studysync
Icon=studysync
Terminal=false
Categories=Education;Office;ProjectManagement;
Keywords=study;task;project;calendar;academic;planner;
StartupWMClass=com-studysync-StudySyncApplication
EOF
    else
        print_info "Using desktop file from: $desktop_source"
        
        # Copy the file
        cp "$desktop_source" "$APPLICATIONS_DIR/studysync.desktop"
        
        # Update entry with absolute path to executable
        # This ensures it runs even if not in PATH
        local escaped_exec="${INSTALL_DIR//\//\\/}\/bin\/studysync"
        sed -i "s|^Exec=.*|Exec=$escaped_exec|" "$APPLICATIONS_DIR/studysync.desktop"
    fi
    
    # Update desktop database if available
    if command -v update-desktop-database &> /dev/null; then
        update-desktop-database "$APPLICATIONS_DIR" 2>/dev/null || true
    fi
    
    # Update icon cache if available
    if command -v gtk-update-icon-cache &> /dev/null; then
        gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
    fi
    
    print_success "Desktop entry installed"
}

setup_config() {
    print_info "Setting up configuration..."
    
    # Create config directory
    mkdir -p "$INSTALL_DIR/config/google"
    
    # Copy application.yml if not exists
    if [[ ! -f "$INSTALL_DIR/config/application.yml" ]]; then
        if [[ -f "$INSTALL_DIR/resources/application.yml.template" ]]; then
            cp "$INSTALL_DIR/resources/application.yml.template" "$INSTALL_DIR/config/application.yml"
        elif [[ -f "$PROJECT_ROOT/src/main/resources/application.yml.template" ]]; then
            cp "$PROJECT_ROOT/src/main/resources/application.yml.template" "$INSTALL_DIR/config/application.yml"
        fi
    fi
    
    # Create data directory for database
    mkdir -p "$INSTALL_DIR/data"
    
    print_success "Configuration set up"
}

print_completion() {
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}          StudySync installed successfully!                ${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "You can now:"
    echo "  • Launch from your application menu (search 'StudySync')"
    echo "  • Run from terminal: studysync"
    echo ""
    echo "Installation location: $INSTALL_DIR"
    echo ""
    
    # Check if ~/.local/bin is in PATH
    if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
        print_warning "~/.local/bin is not in your PATH"
        echo "Add this line to your ~/.bashrc or ~/.zshrc:"
        echo '  export PATH="$HOME/.local/bin:$PATH"'
        echo ""
    fi
    
    echo "To uninstall, run:"
    echo "  $INSTALL_DIR/bin/uninstall.sh"
    echo "  or: ~/.local/share/studysync/bin/uninstall.sh"
}

# Main installation flow
main() {
    print_header
    
    # Parse arguments
    BUILD=false
    for arg in "$@"; do
        case $arg in
            --build|-b)
                BUILD=true
                ;;
            --help|-h)
                echo "Usage: install.sh [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --build, -b    Build from source before installing"
                echo "  --help, -h     Show this help message"
                exit 0
                ;;
        esac
    done
    
    check_java
    
    if [[ "$RELEASE_MODE" == false ]] || [[ "$BUILD" == true ]]; then
        build_application
    fi
    
    install_files
    create_launcher
    install_desktop_entry
    setup_config
    
    # Copy uninstall script
    if [[ -f "$SCRIPT_DIR/uninstall.sh" ]]; then
        cp "$SCRIPT_DIR/uninstall.sh" "$INSTALL_DIR/bin/"
        chmod +x "$INSTALL_DIR/bin/uninstall.sh"
    elif [[ -f "$RELEASE_DIR/uninstall.sh" ]]; then
        cp "$RELEASE_DIR/uninstall.sh" "$INSTALL_DIR/bin/"
        chmod +x "$INSTALL_DIR/bin/uninstall.sh"
    fi
    
    print_completion
}

main "$@"
