# StudySync

<div align="center">
  <img src="src/main/resources/icon.png" alt="StudySync Logo" width="128" height="128">
  
  **A comprehensive Study Management System for academic productivity**
</div>

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)](https://openjfx.io/)
[![H2 Database](https://img.shields.io/badge/Database-H2-blue.svg)](https://www.h2database.com/)
[![Version](https://img.shields.io/badge/Version-0.1.2-red.svg)](https://github.com/geokoko/StudySync/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

StudySync is a comprehensive Study Management System built with modern Java technologies. It combines Spring Boot's robust backend capabilities with JavaFX's rich desktop UI and H2 embedded database for reliable data persistence. The application helps students organize their academic life effectively with integrated task management, study tracking, and project management capabilities.

Perfect for students who want to integrate their academic calendar with task management and study tracking! 📚✨

> **⚠️ Beta Release**: This is version 0.1.2 under active development. Features may change, and some functionality may be incomplete. Please report issues and provide feedback!

## Key Features

StudySync provides comprehensive academic management with three main modules:

## 📚 Study Planner Features, with Daily Reflections Logging
* **Study Sessions**: Track study time with built-in timer and focus level monitoring
* **Study Goals**: Set and manage daily study objectives with future date planning
* **Future Goal Planning**: Navigate to any future date and plan goals ahead via DatePicker
* **Recurring Tasks**: Define repeating task schedules (e.g. every week on Mon/Wed/Fri)
* **Daily Reflections**: Record daily study insights and progress notes
* **Progress Tracking**: Visual progress indicators and session statistics
* **Study Analytics**: Monitor completed sessions and goal achievements

## 📋 Task & Project Management Features  
* **Task Management**: Create, edit, and delete tasks with rich attributes (title, description, category, priority, deadline, status, recurring schedule)
* **Recurring Tasks**: Mark any task as recurring with a weekly/bi-weekly/monthly pattern and specific day-of-week selection
* **Project Management**: Comprehensive project lifecycle management with session logging and progress tracking
* **Category Management**: Create and manage custom categories for better organization
* **Task Reminders**: Set up automated reminders for important deadlines
* **Data Persistence**: All data stored reliably in embedded H2 database

## Google Calendar Integration (**not** finished as of version 0.1.0)
* **OAuth 2.0 Authentication**: Secure Google account login
* **Today's Events**: View all Google Calendar events for the current day
* **Real-time Sync**: Refresh calendar events with one click
* **Event Details**: Display event time, title, location, and description
* **Seamless Integration**: Calendar events displayed alongside study tasks
* **Privacy Focused**: Local credential storage with easy disconnect option

## ☁️ Google Drive Sync
* **Google Sign-in**: Connect your personal Google account directly from the Profile window
* **Drive Storage**: The embedded H2 database is uploaded to a private `StudySync` folder inside your Drive
* **Multi-device ready**: Latest Drive copy is downloaded before the database bootstraps and uploaded whenever the app closes
* **Manual Sync**: Trigger `Sync to Drive now` anytime you want an extra backup mid-session
* **Purely local**: No StudySync backend—OAuth tokens and the H2 file never leave your machine + Google Drive

## Installation & Running the Application

### Quick Install (Linux - Recommended)

**Option 1: Download from GitHub Releases**
```bash
# Download the latest release
curl -LO https://github.com/geokoko/StudySync/releases/latest/download/studysync-linux.tar.gz
curl -LO https://github.com/geokoko/StudySync/releases/latest/download/studysync-linux.tar.gz.sha256

# Verify archive integrity
sha256sum -c studysync-linux.tar.gz.sha256

# Extract and install
tar -xzf studysync-linux.tar.gz
cd studysync-*-linux
./install.sh
```

**Option 2: Build from Source**
```bash
git clone https://github.com/geokoko/StudySync.git
cd StudySync
./scripts/install.sh --build
```

After installation:
- **Launch from app menu**: Search "StudySync" in your application launcher (KDE, GNOME, etc.)
- **Launch from terminal**: Run `studysync`

To uninstall: `~/.local/share/studysync/bin/uninstall.sh`

> Tip: `studysync-linux.tar.gz` is a stable alias for latest release downloads. Versioned files (`studysync-<version>-linux.tar.gz`) are also attached to each release.

---

### Running with Gradle (Development)

1. Clone the Repository
    ``` bash
    git clone https://github.com/geokoko/StudySync.git
    cd StudySync
    ```
2. Configure the application
    ```bash
    cd ./src/main/resources
    cp application.yml.template application.yml
    # Edit application.yml if needed (optional for basic usage)
    ```

3. Run the Application
    * Use the Gradle wrapper (recommended):
        ``` bash
        ./gradlew run   # (Linux/macOS)
        gradlew run     # (Windows)
        ```

    * Use Gradle if you have it installed:
        If you have Gradle>=8.5, then run:
        ``` bash
        gradle build
        gradle run
        ```
        **Note**: Some dependencies of this application are not compatible with the latest version of Gradle (9.0.0) as of right now.

    Gradle will:
    * Automatically download all dependencies (JavaFX, H2 Database, Spring Boot, etc.)
    * Set up the module path correctly for JavaFX
    * Initialize the H2 embedded database
    * Launch the application (com.studysync.StudySyncApplication)

### Additional Run Options

* **Fast startup** (skip some initialization):
    ```bash
    ./scripts/start-fast.sh
    ```

* **Build release package** (for distribution):
    ```bash
    ./scripts/build-release.sh
    # Creates build/release/studysync-VERSION-linux.tar.gz
    ```

## Google Drive Sync Setup (Optional)

1. **Create Google API credentials**
   * Visit [Google Cloud Console](https://console.cloud.google.com/)
   * Enable the **Google Drive API** for your project
   * Create OAuth client credentials of type **Desktop application** and note the client ID/secret
2. **Provide the credentials to StudySync**
   * Copy the template file and fill in the values:
     ```bash
     cp config/google/drive.properties.template config/google/drive.properties
     # edit config/google/drive.properties with your client id/secret
     ```
   * You can override the Drive folder name, redirect port, or where credentials are cached as needed
3. **Run StudySync and connect your Google account**
   * Launch the app, open the **Profile → Google Drive Sync** panel, and click **Sign in with Google**
   * Your browser will handle OAuth locally; tokens are stored in `~/.studysync/google`
4. **Understand the sync flow**
   * On startup, StudySync downloads the latest `studysync.mv.db` from your Drive folder *before* H2 is initialized
   * When the app closes (or you press `Sync to Drive now`), the local database is uploaded back to Google Drive
   * On a brand-new device, sign in once and restart StudySync so the Drive copy is used on the next launch

> The actual database file lives inside `My Drive/StudySync/studysync.mv.db`. No remote StudySync server is involved—the desktop app talks to Google APIs directly.

## Architecture

StudySync uses the **Active Record pattern** with Spring Boot for a clean, maintainable architecture.
See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architectural information and code examples.

## Data Storage

* **Database**: H2 embedded database (`data/studysync.mv.db`)
* **Schema Migrations**: Idempotent `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statements in `schema.sql` ensure safe upgrades for existing databases
* **Cloud Backup (optional)**: When Drive sync is enabled, the same file is mirrored to `My Drive/StudySync/studysync.mv.db`
* **Logs**: Application logs stored in `logs/` directory
* **Configuration**: YAML configuration files in `src/main/resources/`
* **Security**: Encrypted credentials stored locally
