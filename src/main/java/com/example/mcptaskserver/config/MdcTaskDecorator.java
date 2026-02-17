package com.example.mcptaskserver.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * TaskDecorator that propagates MDC context to async threads.
 * This ensures correlation IDs and other MDC values are available in background tasks.
 */
public class MdcTaskDecorator implements TaskDecorator {
    
    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture MDC context from current thread
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        
        return () -> {
            try {
                // Set MDC context in async thread
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                // Clean up MDC
                MDC.clear();
            }
        };
    }
}
