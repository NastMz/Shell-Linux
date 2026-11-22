package com.osproject.shell.server.metrics;

import java.util.List;

/**
 * Provides the most recent resource sample.
 */
public interface MetricsSource {

    /**
     * Returns the latest system metrics.
     *
     * @return the latest snapshot
     */
    MetricsSnapshot metrics();

    /**
     * Returns the latest process list.
     *
     * @return the processes ordered by pid
     */
    List<ProcessInfo> processes();
}
