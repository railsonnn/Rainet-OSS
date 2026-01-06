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
            
            // Execute the script using /import with proper parameter formatting
            log.debug("Executing /import for script: {}", fileName);
            String importCommand = String.format("/import =file-name=%s", fileName);
            conn.execute(importCommand);
            
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
            // The MikroTik API returns each line of the export as a separate result entry
            List<Map<String, String>> result = conn.execute("/export =compact=yes");
            
            // Build the configuration string from the result
            // Each result entry in the list represents a line of the exported configuration
            StringBuilder config = new StringBuilder();
            for (Map<String, String> entry : result) {
                // RouterOS API returns command output in the map values
                // We collect all non-metadata values to reconstruct the export
                for (Map.Entry<String, String> field : entry.entrySet()) {
                    // Skip metadata fields that start with '.'
                    if (!field.getKey().startsWith(".")) {
                        config.append(field.getValue()).append("\n");
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
        
        // Validate fileName to prevent command injection
        String scriptName = fileName.replace(".rsc", "");
        if (!scriptName.matches("^rainet_[0-9]{8}-[0-9]{6}_[a-f0-9]{8}$")) {
            throw new IllegalArgumentException("Invalid script name format");
        }
        
        // RouterOS API doesn't have a direct file upload command
        // We use the /system/script add approach to create the script
        
        try {
            // Remove existing script with same name if exists
            try {
                List<Map<String, String>> existingScripts = conn.execute(
                    String.format("/system/script/print ?name=%s", scriptName));
                if (!existingScripts.isEmpty() && existingScripts.get(0).containsKey(".id")) {
                    String scriptId = existingScripts.get(0).get(".id");
                    conn.execute(String.format("/system/script/remove =.id=%s", scriptId));
                }
            } catch (MikrotikApiException e) {
                // Ignore if script doesn't exist
                log.trace("Script {} doesn't exist yet, continuing", scriptName);
            }
            
            // Add the script with source content
            // Note: The MikroTik API handles parameter escaping internally
            // We construct the command using the proper format with = prefix for parameters
            String addCommand = String.format("/system/script/add =name=%s =source=%s", 
                scriptName, scriptContent);
            conn.execute(addCommand);
            
            // Export the script to a file
            String exportCommand = String.format("/system/script/export =file=%s", scriptName);
            conn.execute(exportCommand);
            
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
        
        // Validate fileName to prevent command injection
        String scriptName = fileName.replace(".rsc", "");
        if (!scriptName.matches("^rainet_[0-9]{8}-[0-9]{6}_[a-f0-9]{8}$")) {
            log.warn("Invalid script name format for cleanup: {}", fileName);
            return;
        }
        
        try {
            // Remove the file
            List<Map<String, String>> files = conn.execute(
                String.format("/file/print ?name=%s", fileName));
            if (!files.isEmpty() && files.get(0).containsKey(".id")) {
                String fileId = files.get(0).get(".id");
                conn.execute(String.format("/file/remove =.id=%s", fileId));
            }
            
            // Also remove the system script
            try {
                List<Map<String, String>> scripts = conn.execute(
                    String.format("/system/script/print ?name=%s", scriptName));
                if (!scripts.isEmpty() && scripts.get(0).containsKey(".id")) {
                    String scriptId = scripts.get(0).get(".id");
                    conn.execute(String.format("/system/script/remove =.id=%s", scriptId));
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
