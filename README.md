# StudySync

<div align="center">
  <img src="src/main/resources/icon.png" alt="StudySync Logo" width="128" height="128">
  
  **A comprehensive Study Management System for academic productivity**
</div>

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)](https://openjfx.io/)
[![H2 Database](https://img.shields.io/badge/Database-H2-blue.svg)](https://www.h2database.com/)
[![Version](https://img.shields.io/badge/Version-0.1.0--BETA-red.svg)](https://github.com/studysync/studysync/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

StudySync is a comprehensive Study Management System built with modern Java technologies. It combines Spring Boot's robust backend capabilities with JavaFX's rich desktop UI and H2 embedded database for reliable data persistence. The application helps students organize their academic life effectively with integrated task management, study tracking, and project management capabilities.

Perfect for students who want to integrate their academic calendar with task management and study tracking! 📚✨

> **⚠️ Beta Release**: This is a beta version (0.1.0-BETA) under active development. Features may change, and some functionality may be incomplete. Please report issues and provide feedback!

## Key Features

StudySync provides comprehensive academic management with three main modules:

## 📚 Study Planner Features, with Daily Reflections Logging
* **Study Sessions**: Track study time with built-in timer and focus level monitoring
* **Study Goals**: Set and manage daily/weekly study objectives  
* **Daily Reflections**: Record daily study insights and progress notes
* **Progress Tracking**: Visual progress indicators and session statistics
* **Study Analytics**: Monitor completed sessions and goal achievements

## 📋 Task & Project Management Features  
* **Task Management**: Create, edit, and delete tasks with rich attributes (title, description, category, priority, deadline, status)
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

## Installation & Running the Application

### Running the application with gradle

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

## Architecture

StudySync uses the **Active Record pattern** with Spring Boot for a clean, maintainable architecture.
See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architectural information and code examples.

## Data Storage

* **Database**: H2 embedded database (`data/studysync.mv.db`)
* **Logs**: Application logs stored in `logs/` directory
* **Configuration**: YAML configuration files in `src/main/resources/`
* **Security**: Encrypted credentials stored locally
