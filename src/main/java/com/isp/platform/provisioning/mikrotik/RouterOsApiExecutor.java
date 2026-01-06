package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;
import lombok.extern.slf4j.Slf4j;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import org.springframework.stereotype.Service;

import javax.net.SocketFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real MikroTik RouterOS API executor using me.legrange mikrotik library.
 * Connects via RouterOS API (TCP port 8728) and executes commands on real equipment.
 */
@Slf4j
@Service
public class RouterOsApiExecutor implements RouterOsExecutor {

    private static final int API_PORT = 8728;
    private static final int TIMEOUT_MS = 30000;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    public boolean testConnection(Router router) {
        ApiConnection conn = null;
        try {
            log.info("Testing connection to router: {} ({})", router.getHostname(), router.getManagementAddress());
            
            SocketFactory socketFactory = SocketFactory.getDefault();
            conn = ApiConnection.connect(socketFactory, router.getManagementAddress(), API_PORT, TIMEOUT_MS);
            conn.login(router.getApiUsername(), router.getApiPassword());
            
            log.info("Connection test successful for router: {}", router.getHostname());
            return true;
        } catch (MikrotikApiException e) {
            log.error("Connection test failed for router: {}", router.getHostname(), e);
            throw new RuntimeException("Failed to connect to router: " + router.getHostname(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    log.warn("Error closing connection to router: {}", router.getHostname(), e);
                }
            }
        }
    }

    @Override
    public void applyScript(Router router, String script) {
        ApiConnection conn = null;
        try {
            log.info("Applying script to router: {}", router.getHostname());
            
            SocketFactory socketFactory = SocketFactory.getDefault();
            conn = ApiConnection.connect(socketFactory, router.getManagementAddress(), API_PORT, TIMEOUT_MS);
            conn.login(router.getApiUsername(), router.getApiPassword());
            
            // Create temporary script file on router
            String scriptName = generateScriptName();
            String fileName = scriptName + ".rsc";
            
            // Upload script content to router via /system/script
            uploadScriptToRouter(conn, fileName, script);
            
            // Execute the script using /import
            log.debug("Executing /import for script: {}", fileName);
            conn.execute("/import file-name=" + fileName);
            
            // Clean up temporary script file
            removeScriptFile(conn, fileName);
            
            log.info("Script successfully applied to router: {}", router.getHostname());
        } catch (MikrotikApiException e) {
            log.error("Failed to apply script to router: {}", router.getHostname(), e);
            throw new RuntimeException("Failed to apply script to router: " + router.getHostname(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    log.warn("Error closing connection to router: {}", router.getHostname(), e);
                }
            }
        }
    }

    @Override
    public String exportCompact(Router router) {
        ApiConnection conn = null;
        try {
            log.info("Exporting compact configuration from router: {}", router.getHostname());
            
            SocketFactory socketFactory = SocketFactory.getDefault();
            conn = ApiConnection.connect(socketFactory, router.getManagementAddress(), API_PORT, TIMEOUT_MS);
            conn.login(router.getApiUsername(), router.getApiPassword());
            
            // Execute /export compact command
            List<Map<String, String>> result = conn.execute("/export compact=yes");
            
            // Build the configuration string from the result
            StringBuilder config = new StringBuilder();
            for (Map<String, String> line : result) {
                // The export command returns lines with a "line" key or direct values
                if (line.containsKey("line")) {
                    config.append(line.get("line")).append("\n");
                } else {
                    // Some results may have all values concatenated
                    for (String value : line.values()) {
                        config.append(value).append("\n");
                    }
                }
            }
            
            String exportedConfig = config.toString();
            log.info("Configuration exported from router: {} ({} bytes)", 
                    router.getHostname(), exportedConfig.length());
            return exportedConfig;
        } catch (MikrotikApiException e) {
            log.error("Failed to export configuration from router: {}", router.getHostname(), e);
            throw new RuntimeException("Failed to export configuration from router: " + router.getHostname(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    log.warn("Error closing connection to router: {}", router.getHostname(), e);
                }
            }
        }
    }

    /**
     * Upload script content to router as a file.
     * Uses /system/script to create the script, then exports to file.
     */
    private void uploadScriptToRouter(ApiConnection conn, String fileName, String scriptContent) 
            throws MikrotikApiException {
        
        log.debug("Uploading script '{}' to router ({} bytes)", fileName, scriptContent.length());
        
        // RouterOS API doesn't have a direct file upload command
        // We use the /system/script add approach to create the script
        
        // Add script as a system script, then export it to file
        String scriptName = fileName.replace(".rsc", "");
        
        try {
            // Remove existing script with same name if exists
            try {
                List<Map<String, String>> existingScripts = conn.execute("/system/script/print where name=\"" + scriptName + "\"");
                if (!existingScripts.isEmpty()) {
                    conn.execute("/system/script/remove numbers=" + existingScripts.get(0).get(".id"));
                }
            } catch (MikrotikApiException e) {
                // Ignore if script doesn't exist
                log.trace("Script {} doesn't exist yet, continuing", scriptName);
            }
            
            // Add the script with source content
            conn.execute("/system/script/add name=" + scriptName + " source=\"" + scriptContent.replace("\"", "\\\"") + "\"");
            
            // Export the script to a file
            conn.execute("/system/script/export file=" + scriptName);
            
        } catch (MikrotikApiException e) {
            log.error("Failed to upload script to router", e);
            throw new RuntimeException("Failed to upload script content", e);
        }
    }

    /**
     * Remove script file from router.
     */
    private void removeScriptFile(ApiConnection conn, String fileName) {
        log.debug("Removing script file '{}' from router", fileName);
        
        try {
            // Remove the file
            List<Map<String, String>> files = conn.execute("/file/print where name=\"" + fileName + "\"");
            if (!files.isEmpty()) {
                conn.execute("/file/remove numbers=" + files.get(0).get(".id"));
            }
            
            // Also remove the system script
            String scriptName = fileName.replace(".rsc", "");
            try {
                List<Map<String, String>> scripts = conn.execute("/system/script/print where name=\"" + scriptName + "\"");
                if (!scripts.isEmpty()) {
                    conn.execute("/system/script/remove numbers=" + scripts.get(0).get(".id"));
                }
            } catch (MikrotikApiException e) {
                log.trace("System script cleanup failed, may not exist", e);
            }
        } catch (MikrotikApiException e) {
            log.warn("Failed to remove temporary script file: {}", fileName, e);
            // Don't fail the entire operation if cleanup fails
        }
    }

    /**
     * Generate a unique script name.
     */
    private String generateScriptName() {
        return String.format("rainet_%s_%s", 
                TIMESTAMP_FORMATTER.format(LocalDateTime.now()), 
                UUID.randomUUID().toString().substring(0, 8));
    }
}
