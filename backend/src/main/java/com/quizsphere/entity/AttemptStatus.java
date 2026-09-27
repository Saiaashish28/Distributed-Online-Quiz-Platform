package com.quizsphere.entity;

public enum AttemptStatus {
    IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED;

    public boolean isFinal() {
        return this != IN_PROGRESS;
    }
}
