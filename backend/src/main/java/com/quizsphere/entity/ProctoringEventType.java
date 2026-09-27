package com.quizsphere.entity;

public enum ProctoringEventType {
    FOCUS_LOST, FOCUS_RETURNED, FULLSCREEN_EXIT, FULLSCREEN_ENTER;

    /** Events that count toward the warning threshold. */
    public boolean isWarning() {
        return this == FOCUS_LOST || this == FULLSCREEN_EXIT;
    }
}
