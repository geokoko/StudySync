package com.studysync.presentation.ui.components;

import javafx.scene.Node;

public interface RefreshablePanel {
    Node getView();

    void updateDisplay();

    /**
     * Persists any editor state that has not reached the database yet.
     *
     * @return {@code false} when shutdown or another destructive transition
     *         must stop so the user can retry without losing their work
     */
    default boolean flushPendingChanges() {
        return true;
    }
}
