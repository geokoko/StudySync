#!/bin/bash
#
# StudySync Uninstaller for Linux
# Removes StudySync installation
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

print_header() {
    echo -e "${BLUE}"
    echo "╔═══════════════════════════════════════════════════════════╗"
    echo "║                  StudySync Uninstaller                    ║"
    echo "╚═══════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}→ $1${NC}"
}

confirm_uninstall() {
    echo "This will remove StudySync from your system."
    echo ""
    echo "The following will be deleted:"
    echo "  • $INSTALL_DIR (application files)"
    echo "  • $BIN_DIR/studysync (launcher symlink)"
    echo "  • $APPLICATIONS_DIR/studysync.desktop"
    echo "  • $ICONS_DIR/studysync.png"
    echo ""
    
    read -p "Do you want to keep your data (database, settings)? [Y/n] " keep_data
    keep_data=${keep_data:-Y}
    
    read -p "Proceed with uninstallation? [y/N] " confirm
    confirm=${confirm:-N}
    
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "Uninstallation cancelled."
        exit 0
    fi
    
    if [[ "$keep_data" =~ ^[Yy]$ ]]; then
        KEEP_DATA=true
    else
        KEEP_DATA=false
    fi
}

remove_files() {
    print_info "Removing StudySync files..."
    
    # Remove launcher symlink
    if [[ -L "$BIN_DIR/studysync" ]]; then
        rm -f "$BIN_DIR/studysync"
        print_success "Removed launcher symlink"
    fi
    
    # Remove desktop entry
    if [[ -f "$APPLICATIONS_DIR/studysync.desktop" ]]; then
        rm -f "$APPLICATIONS_DIR/studysync.desktop"
        print_success "Removed desktop entry"
    fi
    
    # Remove icon
    if [[ -f "$ICONS_DIR/studysync.png" ]]; then
        rm -f "$ICONS_DIR/studysync.png"
        print_success "Removed icon"
    fi
    
    # Remove installation directory
    if [[ -d "$INSTALL_DIR" ]]; then
        if [[ "$KEEP_DATA" == true ]]; then
            # Keep data directory
            print_info "Preserving data directory..."
            
            # Backup data
            if [[ -d "$INSTALL_DIR/data" ]]; then
                mkdir -p "$HOME/.studysync-backup"
                cp -r "$INSTALL_DIR/data" "$HOME/.studysync-backup/"
                print_success "Data backed up to ~/.studysync-backup/data"
            fi
            
            # Remove everything except data
            find "$INSTALL_DIR" -mindepth 1 -maxdepth 1 ! -name 'data' -exec rm -rf {} +
            
            # If only data remains, keep it; otherwise remove the dir
            if [[ -z "$(ls -A "$INSTALL_DIR" 2>/dev/null)" ]]; then
                rmdir "$INSTALL_DIR" 2>/dev/null || true
            fi
        else
            rm -rf "$INSTALL_DIR"
        fi
        print_success "Removed installation directory"
    fi
    
    # Update desktop database
    if command -v update-desktop-database &> /dev/null; then
        update-desktop-database "$APPLICATIONS_DIR" 2>/dev/null || true
    fi
    
    # Update icon cache
    if command -v gtk-update-icon-cache &> /dev/null; then
        gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
    fi
}

print_completion() {
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}         StudySync uninstalled successfully!               ${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
    echo ""
    
    if [[ "$KEEP_DATA" == true ]]; then
        echo "Your data has been preserved at: ~/.studysync-backup/data"
        echo "You can restore it if you reinstall StudySync."
    fi
    
    echo ""
    echo "Thank you for using StudySync!"
}

# Main uninstallation flow
main() {
    print_header
    
    # Check for --force flag
    FORCE=false
    for arg in "$@"; do
        case $arg in
            --force|-f)
                FORCE=true
                ;;
            --help|-h)
                echo "Usage: uninstall.sh [OPTIONS]"
                echo ""
                echo "Options:"
                echo "  --force, -f    Skip confirmation prompts"
                echo "  --help, -h     Show this help message"
                exit 0
                ;;
        esac
    done
    
    if [[ "$FORCE" == true ]]; then
        KEEP_DATA=true
    else
        confirm_uninstall
    fi
    
    remove_files
    print_completion
}

main "$@"
