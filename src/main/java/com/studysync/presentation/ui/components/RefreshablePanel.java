package com.studysync.presentation.ui.components;

import javafx.scene.Node;

public interface RefreshablePanel {
    Node getView();
    void updateDisplay();
}
