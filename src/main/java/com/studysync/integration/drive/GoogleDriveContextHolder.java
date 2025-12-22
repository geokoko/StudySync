package com.studysync.integration.drive;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores the Google Drive settings so they can be shared between the bootstrap logic and Spring context.
 */
public final class GoogleDriveContextHolder {

    private static final AtomicReference<GoogleDriveSettings> SETTINGS = new AtomicReference<>();

    private GoogleDriveContextHolder() {
    }

    public static void set(GoogleDriveSettings settings) {
        SETTINGS.set(settings);
    }

    public static GoogleDriveSettings get() {
        return SETTINGS.get();
    }
}
