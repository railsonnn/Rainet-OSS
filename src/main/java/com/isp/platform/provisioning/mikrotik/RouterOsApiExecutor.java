package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;
import lombok.extern.slf4j.Slf4j;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
            
            conn = ApiConnection.connect(router.getManagementAddress(), API_PORT, TIMEOUT_MS);
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
            
            conn = ApiConnection.connect(router.getManagementAddress(), API_PORT, TIMEOUT_MS);
            conn.login(router.getApiUsername(), router.getApiPassword());
            
            // Create temporary script file on router
            String scriptName = generateScriptName();
            String fileName = scriptName + ".rsc";
            
            // Upload script content to router via /file/set
            // RouterOS API requires us to send the file content line by line
            uploadScriptToRouter(conn, fileName, script);
            
            // Execute the script using /import
            log.debug("Executing /import for script: {}", fileName);
            conn.execute("/import", "file-name=" + fileName);
            
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
            
            conn = ApiConnection.connect(router.getManagementAddress(), API_PORT, TIMEOUT_MS);
            conn.login(router.getApiUsername(), router.getApiPassword());
            
            // Execute /export compact command
            List<Map<String, String>> result = conn.execute("/export", "compact=yes");
            
            // Build the configuration string from the result
            StringBuilder config = new StringBuilder();
            for (Map<String, String> line : result) {
                // The export command returns lines with a "line" key
                if (line.containsKey("line")) {
                    config.append(line.get("line")).append("\n");
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
     * Uses /file/print command to create file content.
     */
    private void uploadScriptToRouter(ApiConnection conn, String fileName, String scriptContent) 
            throws MikrotikApiException {
        
        log.debug("Uploading script '{}' to router ({} bytes)", fileName, scriptContent.length());
        
        // RouterOS API doesn't have a direct file upload command
        // We need to use FTP or create the file through system commands
        // For simplicity, we'll use the /system/script add approach
        
        // Add script as a system script, then export it to file
        String scriptName = fileName.replace(".rsc", "");
        
        try {
            // Remove existing script with same name if exists
            try {
                conn.execute("/system/script/remove", "numbers=[find name=\"" + scriptName + "\"]");
            } catch (MikrotikApiException e) {
                // Ignore if script doesn't exist
                log.trace("Script {} doesn't exist yet, continuing", scriptName);
            }
            
            // Add the script
            conn.execute("/system/script/add", 
                "name=" + scriptName, 
                "source=" + scriptContent);
            
            // Export the script to a file
            conn.execute("/system/script/export", 
                "file=" + scriptName);
            
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
            conn.execute("/file/remove", "numbers=[find name=\"" + fileName + "\"]");
            
            // Also remove the system script
            String scriptName = fileName.replace(".rsc", "");
            try {
                conn.execute("/system/script/remove", "numbers=[find name=\"" + scriptName + "\"]");
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
