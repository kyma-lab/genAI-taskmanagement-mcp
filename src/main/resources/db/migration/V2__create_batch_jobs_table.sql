-- MCP Task Server - Batch Jobs Table
-- Creates batch_jobs table for tracking asynchronous batch operations

-- Create batch_jobs table
CREATE TABLE batch_jobs (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(50) NOT NULL,
    total_tasks INTEGER NOT NULL,
    processed_tasks INTEGER DEFAULT 0,
    error_message TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_batch_jobs_status ON batch_jobs(status);
CREATE INDEX idx_batch_jobs_created_at ON batch_jobs(created_at);
CREATE INDEX idx_batch_jobs_completed_at ON batch_jobs(completed_at);

-- Add check constraint for status values
ALTER TABLE batch_jobs ADD CONSTRAINT chk_batch_jobs_status 
    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'));

-- Comments for documentation
COMMENT ON TABLE batch_jobs IS 'Asynchronous batch job tracking';
COMMENT ON COLUMN batch_jobs.id IS 'UUID primary key';
COMMENT ON COLUMN batch_jobs.status IS 'Job status: PENDING, RUNNING, COMPLETED, or FAILED';
COMMENT ON COLUMN batch_jobs.total_tasks IS 'Total number of tasks in the batch';
COMMENT ON COLUMN batch_jobs.processed_tasks IS 'Number of tasks processed so far';
COMMENT ON COLUMN batch_jobs.error_message IS 'Error message if job failed';
COMMENT ON COLUMN batch_jobs.duration_ms IS 'Processing duration in milliseconds';
COMMENT ON COLUMN batch_jobs.created_at IS 'Timestamp when job was created';
COMMENT ON COLUMN batch_jobs.updated_at IS 'Timestamp when job was last updated';
COMMENT ON COLUMN batch_jobs.completed_at IS 'Timestamp when job completed (success or failure)';
