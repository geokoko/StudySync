package com.studysync.presentation.ui.components;

import com.studysync.domain.service.StudyService;
import com.studysync.domain.service.TaskService;
import com.studysync.integration.drive.GoogleDriveService;
import com.studysync.integration.drive.GoogleDriveService.SyncStatus;
import com.studysync.presentation.ui.StudySyncUI;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartupSyncResolutionTest {
    private GoogleDriveService drive;
    private StudyService study;
    private TaskService tasks;
    private RefreshablePanel panel;
    private StudySyncUI ui;
    private Button badge;

    @BeforeAll
    static void startJavaFx() throws Exception {
        SessionVisibilityTest.startJavaFx();
    }

    @BeforeEach
    void setUp() throws Exception {
        drive = mock(GoogleDriveService.class);
        when(drive.isIntegrationEnabled()).thenReturn(true);
        when(drive.isSignedIn()).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(drive).runStartupMaintenanceWithoutDirtyTracking(any());
        study = mock(StudyService.class);
        when(study.processAllDelayedGoals()).thenReturn(new StudyService.GoalDelayProcessingResult(1));
        tasks = mock(TaskService.class);
        panel = mock(RefreshablePanel.class);
        onFx(() -> {
            // Exercise the real sync callbacks without building every panel or opening a window.
            // This is the held state left after the startup check defers maintenance.
            ui = mock(StudySyncUI.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(ui, "googleDriveService", drive);
            ReflectionTestUtils.setField(ui, "studyService", study);
            ReflectionTestUtils.setField(ui, "taskService", tasks);
            ReflectionTestUtils.setField(ui, "panelMap", Map.of(new Tab(), panel));
            badge = new Button();
            ReflectionTestUtils.setField(ui, "syncStatusBadge", badge);
        });
    }

    @ParameterizedTest
    @EnumSource(value = SyncStatus.class, names = {"UP_TO_DATE", "LOCAL_NEWER", "DISABLED"})
    void resolvingConflictRunsDeferredMaintenanceOnce(final SyncStatus status) throws Exception {
        refresh(SyncStatus.CONFLICT);
        verify(study, never()).releaseOverdueSweep();
        if (status == SyncStatus.DISABLED) {
            when(drive.isSignedIn()).thenReturn(false);
        }
        refresh(status);
        refresh(status);

        var order = inOrder(drive, study, tasks, panel);
        order.verify(drive).runStartupMaintenanceWithoutDirtyTracking(any());
        order.verify(study).releaseOverdueSweep();
        order.verify(tasks).markDelayedTasks();
        order.verify(study).normalizeAchievedGoalChecklists();
        order.verify(study).processAllDelayedGoals();
        order.verify(panel).updateDisplay();
        verify(study).releaseOverdueSweep();
        verify(study).processAllDelayedGoals();
        verify(study).normalizeAchievedGoalChecklists();
        verify(tasks).markDelayedTasks();
        verify(panel).updateDisplay();
        verify(drive, never()).markLocalDbDirty();
        if (status == SyncStatus.DISABLED) {
            onFx(() -> assertFalse(badge.isVisible()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = SyncStatus.class, names = {"CONFLICT", "DRIVE_NEWER", "UNKNOWN"})
    void unresolvedStatusKeepsMaintenanceHeld(final SyncStatus status) throws Exception {
        refresh(SyncStatus.DRIVE_NEWER);
        refresh(status);
        verify(study, never()).releaseOverdueSweep();
        verify(study, never()).normalizeAchievedGoalChecklists();
        verify(drive, never()).runStartupMaintenanceWithoutDirtyTracking(any());
    }

    @ParameterizedTest
    @EnumSource(value = SyncStatus.class, names = {"UP_TO_DATE", "LOCAL_NEWER", "DISABLED"})
    void pendingOrFailedDownloadBlocksEvenASafeStatus(final SyncStatus status) throws Exception {
        when(drive.hasPendingDownload()).thenReturn(true);
        refresh(status);
        verify(study, never()).releaseOverdueSweep();
        verify(study, never()).normalizeAchievedGoalChecklists();

        when(drive.hasPendingDownload()).thenReturn(false);
        when(drive.hasFailedPendingDownload()).thenReturn(true);
        refresh(status);
        verify(study, never()).releaseOverdueSweep();
        verify(study, never()).normalizeAchievedGoalChecklists();

        when(drive.hasFailedPendingDownload()).thenReturn(false);
        refresh(status);
        verify(study).releaseOverdueSweep();
        verify(study).normalizeAchievedGoalChecklists();
    }

    @Test
    void failedCheckDoesNotReleaseAnEarlierConflict() throws Exception {
        refresh(SyncStatus.CONFLICT);
        when(drive.checkSyncStatus()).thenThrow(new IllegalStateException("offline"));
        awaitRefresh();
        verify(study, never()).releaseOverdueSweep();
    }

    @ParameterizedTest
    @ValueSource(strings = {"refreshSyncStatusBadge", "resolveDriveSyncOnStartup"})
    void staleSafeCheckCannotOverrideANewerConflict(final String checkMethod) throws Exception {
        onFx(() -> {
            CompletableFuture<SyncStatus> pendingCheck = new CompletableFuture<>();
            try (var futures = mockStatic(CompletableFuture.class, CALLS_REAL_METHODS)) {
                futures.when(() -> CompletableFuture.supplyAsync(any())).thenReturn(pendingCheck);
                ReflectionTestUtils.invokeMethod(ui, checkMethod);
            }
            ReflectionTestUtils.invokeMethod(ui, "applySyncStatusBadge", SyncStatus.CONFLICT);
            pendingCheck.complete(SyncStatus.UP_TO_DATE);
        });
        onFx(() -> verify(study, never()).releaseOverdueSweep());
    }

    private void refresh(final SyncStatus status) throws Exception {
        when(drive.checkSyncStatus()).thenReturn(status);
        awaitRefresh();
    }

    private void awaitRefresh() throws Exception {
        CompletableFuture<Void> refreshed = new CompletableFuture<>();
        onFx(() -> {
            // setManaged is last for both a visible badge and a hidden (signed-out) one.
            // The next FX turn observes maintenance after the whole callback finishes.
            badge.managedProperty().addListener((observable, oldValue, newValue) -> {
                if (!badge.getText().equals("Checking sync…") || !newValue) {
                    Platform.runLater(() -> refreshed.complete(null));
                }
            });
            badge.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.equals("Checking sync…")) {
                    Platform.runLater(() -> refreshed.complete(null));
                }
            });
            ReflectionTestUtils.invokeMethod(ui, "refreshSyncStatusBadge");
        });
        refreshed.get(10, TimeUnit.SECONDS);
    }

    private static void onFx(final Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
