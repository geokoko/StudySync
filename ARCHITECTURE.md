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

### **Key Characteristics**
- **Active Record Pattern**: Models handle their own database operations
- **Simplified Services**: Services focus on business logic and orchestration
- **No Database Service Layer**: Eliminated unnecessary abstraction layer
- **Self-Managing Models**: `task.save()`, `project.delete()`, `StudySession.findAll()`
- **Mutable Domain Objects**: Task, Project, StudySession are classes with behavior
- **Real-time Session Tracking**: Live timers with Duration-based calculations
- **Direct JDBC**: Models use JdbcTemplate directly for database operations

