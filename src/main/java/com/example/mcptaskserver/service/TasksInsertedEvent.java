package com.example.mcptaskserver.service;

import org.springframework.context.ApplicationEvent;

/**
 * Spring event published after a batch of tasks has been successfully inserted.
 * Used to trigger MCP resource-list-changed notifications to connected clients.
 */
public class TasksInsertedEvent extends ApplicationEvent {

    private final String jobId;
    private final int taskCount;

    public TasksInsertedEvent(Object source, String jobId, int taskCount) {
        super(source);
        this.jobId = jobId;
        this.taskCount = taskCount;
    }

    public String getJobId() {
        return jobId;
    }

    public int getTaskCount() {
        return taskCount;
    }
}
