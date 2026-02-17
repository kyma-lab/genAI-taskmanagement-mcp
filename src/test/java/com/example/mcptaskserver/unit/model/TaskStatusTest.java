package com.example.mcptaskserver.unit.model;

import com.example.mcptaskserver.model.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStatusTest {

    @Test
    void fromValue_shouldReturnTodo_whenValidTodoValue() {
        assertThat(TaskStatus.fromValue("TODO")).isEqualTo(TaskStatus.TODO);
        assertThat(TaskStatus.fromValue("todo")).isEqualTo(TaskStatus.TODO);
        assertThat(TaskStatus.fromValue("Todo")).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void fromValue_shouldReturnInProgress_whenValidInProgressValue() {
        assertThat(TaskStatus.fromValue("IN_PROGRESS")).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(TaskStatus.fromValue("in_progress")).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(TaskStatus.fromValue("In_Progress")).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void fromValue_shouldReturnDone_whenValidDoneValue() {
        assertThat(TaskStatus.fromValue("DONE")).isEqualTo(TaskStatus.DONE);
        assertThat(TaskStatus.fromValue("done")).isEqualTo(TaskStatus.DONE);
        assertThat(TaskStatus.fromValue("Done")).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void fromValue_shouldThrowException_whenNullValue() {
        assertThatThrownBy(() -> TaskStatus.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskStatus value cannot be null or blank");
    }

    @Test
    void fromValue_shouldThrowException_whenBlankValue() {
        assertThatThrownBy(() -> TaskStatus.fromValue(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskStatus value cannot be null or blank");
        
        assertThatThrownBy(() -> TaskStatus.fromValue("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskStatus value cannot be null or blank");
    }

    @Test
    void fromValue_shouldThrowException_whenInvalidValue() {
        assertThatThrownBy(() -> TaskStatus.fromValue("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid TaskStatus value");
    }

    @Test
    void fromValue_shouldThrowException_whenLengthExceeds20Characters() {
        String longValue = "A".repeat(21);
        assertThatThrownBy(() -> TaskStatus.fromValue(longValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TaskStatus value exceeds maximum length");
    }

    @Test
    void fromValue_shouldAcceptValue_whenLengthIs20Characters() {
        // This should throw because it's invalid, but not because of length
        String exactlyTwenty = "A".repeat(20);
        assertThatThrownBy(() -> TaskStatus.fromValue(exactlyTwenty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid TaskStatus value");
    }
}
