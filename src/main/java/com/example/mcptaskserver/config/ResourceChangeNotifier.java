package com.example.mcptaskserver.config;

import com.example.mcptaskserver.service.TasksInsertedEvent;
import io.modelcontextprotocol.server.McpSyncServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Sends MCP resource-list-changed notifications to all connected clients
 * after tasks have been successfully committed to the database.
 *
 * Uses {@code @TransactionalEventListener(AFTER_COMMIT)} so the notification
 * is sent only after the inserting transaction has committed and the new data
 * is visible to subsequent reads.
 *
 * Uses {@code ObjectProvider<McpSyncServer>} for optional injection — if no
 * MCP server beans are registered the notifier is a no-op instead of failing
 * at startup.
 */
@Component
@Slf4j
public class ResourceChangeNotifier {

    private final ObjectProvider<McpSyncServer> mcpServersProvider;

    public ResourceChangeNotifier(ObjectProvider<McpSyncServer> mcpServersProvider) {
        this.mcpServersProvider = mcpServersProvider;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTasksInserted(TasksInsertedEvent event) {
        List<McpSyncServer> mcpServers = mcpServersProvider.stream().toList();
        if (mcpServers.isEmpty()) {
            return;
        }
        log.debug("Notifying {} MCP server(s) of resource changes after job {} ({} tasks)",
            mcpServers.size(), event.getJobId(), event.getTaskCount());
        for (McpSyncServer server : mcpServers) {
            try {
                server.notifyResourcesListChanged();
            } catch (Exception e) {
                log.debug("Resource list-changed notification failed (best-effort): {}", e.getMessage());
            }
        }
    }
}
