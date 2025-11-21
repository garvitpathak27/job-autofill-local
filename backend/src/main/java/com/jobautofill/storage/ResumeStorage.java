package com.jobautofill.storage;

import com.jobautofill.model.ResumeData;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple in-memory storage for resume data.
 * In production, this would be a database (SQLite, PostgreSQL, etc.)
 * For now, we store only ONE resume at a time (latest upload overwrites).
 */
@Component
public class ResumeStorage {

    private final AtomicReference<ResumeData> currentResume = new AtomicReference<>();

    public void store(ResumeData resumeData) {
        currentResume.set(resumeData);
    }

    public ResumeData get() {
        return currentResume.get();
    }

    public boolean hasResume() {
        return currentResume.get() != null;
    }

    public void clear() {
        currentResume.set(null);
    }
}
