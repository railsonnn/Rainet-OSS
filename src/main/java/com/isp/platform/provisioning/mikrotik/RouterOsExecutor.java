package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;

public interface RouterOsExecutor {

    /**
     * Test connection to a MikroTik router.
     *
     * @param router the router to connect to
     * @return true if connection successful
     * @throws RuntimeException if connection fails
     */
    boolean testConnection(Router router);

    /**
     * Apply a complete RouterOS script to the router.
     * Uses /import command for idempotency.
     *
     * @param router the target router
     * @param script the complete RouterOS script
     * @throws RuntimeException if execution fails
     */
    void applyScript(Router router, String script);

    /**
     * Export the complete router configuration.
     *
     * @param router the source router
     * @return compact export of router configuration
     * @throws RuntimeException if export fails
     */
    String exportCompact(Router router);
}
