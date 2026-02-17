-- MCP Task Server - Initial Schema
-- Creates tasks table with optimized SEQUENCE for batch inserts

-- Create sequence with allocationSize=50 for batch optimization
CREATE SEQUENCE task_sequence START WITH 1 INCREMENT BY 50;

-- Create tasks table
CREATE TABLE tasks (
    id BIGINT PRIMARY KEY DEFAULT nextval('task_sequence'),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    due_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
CREATE INDEX idx_tasks_created_at ON tasks(created_at);

-- Add check constraint for status values
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_status 
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE'));

-- Comments for documentation
COMMENT ON TABLE tasks IS 'Task management table for MCP server';
COMMENT ON COLUMN tasks.id IS 'Primary key, auto-generated from sequence';
COMMENT ON COLUMN tasks.title IS 'Task title, required, max 255 chars';
COMMENT ON COLUMN tasks.description IS 'Optional task description';
COMMENT ON COLUMN tasks.status IS 'Task status: TODO, IN_PROGRESS, or DONE';
COMMENT ON COLUMN tasks.due_date IS 'Optional due date';
COMMENT ON COLUMN tasks.created_at IS 'Timestamp when task was created';
COMMENT ON COLUMN tasks.updated_at IS 'Timestamp when task was last updated';
