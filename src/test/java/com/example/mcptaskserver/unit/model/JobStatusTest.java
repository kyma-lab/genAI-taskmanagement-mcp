package com.example.mcptaskserver.unit.model;

import com.example.mcptaskserver.model.JobStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobStatusTest {

    @Test
    void fromValue_shouldReturnPending_whenValidPendingValue() {
        assertThat(JobStatus.fromValue("PENDING")).isEqualTo(JobStatus.PENDING);
        assertThat(JobStatus.fromValue("pending")).isEqualTo(JobStatus.PENDING);
        assertThat(JobStatus.fromValue("Pending")).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void fromValue_shouldReturnRunning_whenValidRunningValue() {
        assertThat(JobStatus.fromValue("RUNNING")).isEqualTo(JobStatus.RUNNING);
        assertThat(JobStatus.fromValue("running")).isEqualTo(JobStatus.RUNNING);
        assertThat(JobStatus.fromValue("Running")).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void fromValue_shouldReturnCompleted_whenValidCompletedValue() {
        assertThat(JobStatus.fromValue("COMPLETED")).isEqualTo(JobStatus.COMPLETED);
        assertThat(JobStatus.fromValue("completed")).isEqualTo(JobStatus.COMPLETED);
        assertThat(JobStatus.fromValue("Completed")).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void fromValue_shouldReturnFailed_whenValidFailedValue() {
        assertThat(JobStatus.fromValue("FAILED")).isEqualTo(JobStatus.FAILED);
        assertThat(JobStatus.fromValue("failed")).isEqualTo(JobStatus.FAILED);
        assertThat(JobStatus.fromValue("Failed")).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void fromValue_shouldThrowException_whenNullValue() {
        assertThatThrownBy(() -> JobStatus.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JobStatus value cannot be null");
    }

    @Test
    void fromValue_shouldThrowException_whenInvalidValue() {
        assertThatThrownBy(() -> JobStatus.fromValue("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JobStatus value");
    }
}
