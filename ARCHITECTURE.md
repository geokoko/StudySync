# StudySync Architecture

## Current Architecture (Active Record Pattern + MVC)

### **Active Record Architecture**
```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │   JavaFX UI     │  │ REST Controllers │  │ RefreshablePanel│
│  │   Components    │  │ (TaskRestController)│ │  Interface     │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                Simplified Service Layer                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │   TaskService   │  │  StudyService   │  │ProjectService│ │
│  │ CategoryService │  │ ReminderService │  │   ...more   │  │
│  │ (Business Logic │  │   Orchestration) │  │             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│            Active Record Domain Models                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │      Task       │  │   StudySession  │  │   Project   │  │
│  │  • save()       │  │  • save()       │  │ • save()    │  │
│  │  • delete()     │  │  • delete()     │  │ • delete()  │  │
│  │  • findAll()    │  │  • findAll()    │  │ • findAll() │  │
│  │  • findById()   │  │  • findById()   │  │ • findById()│  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ ProjectSession  │  │   TaskPriority  │  │TaskCategory │  │
│  │  • save()       │  │   TaskStatus    │  │DailyReflection│
│  │  • delete()     │  │  ProjectStatus  │  │   ...more   │  │
│  │  • findAll()    │  │  (Value Objects)│  │             │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────────────┐
                    │   H2 Database   │
                    │  (Direct JDBC)  │
                    └─────────────────┘
```

## Google Drive Sync Flow

When the optional Google Drive integration is configured, the persistence layer gains an additional offline-first sync loop:

1. **Bootstrap** – before Spring Boot initializes the datasource, the `GoogleDriveBootstrap` downloads the latest `studysync.mv.db` from the signed-in Google Drive account (if cached credentials exist).
2. **Runtime** – StudySync continues to operate against the local H2 file for fast, offline reads/writes. Users can trigger a manual upload from the Profile → Google Drive Sync panel at any time.
3. **Shutdown** – `GoogleDriveService` uploads the updated H2 file to the user's private `StudySync` Drive folder during bean destruction, ensuring multi-device availability without a StudySync backend.

OAuth credentials live on the user's machine (`~/.studysync/google`) and the cloud copy resides inside the user's own Drive (`My Drive/StudySync/studysync.mv.db`).

### **Key Characteristics**
- **Active Record Pattern**: Models handle their own database operations
- **Simplified Services**: Services focus on business logic and orchestration
- **No Database Service Layer**: Eliminated unnecessary abstraction layer
- **Self-Managing Models**: `task.save()`, `project.delete()`, `StudySession.findAll()`
- **Mutable Domain Objects**: Task, Project, StudySession are classes with behavior
- **Real-time Session Tracking**: Live timers with Duration-based calculations
- **Direct JDBC**: Models use JdbcTemplate directly for database operations
