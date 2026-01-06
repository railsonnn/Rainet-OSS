package com.isp.platform.provisioning.mikrotik;

import com.isp.platform.provisioning.domain.Router;
import org.springframework.stereotype.Component;

@Component
public class RouterOsScriptGenerator {

    public String generateProvisioningScript(Router router) {
        // TODO: generate full RouterOS script including PPPoE, RADIUS, QoS profiles
        return "# Generated RouterOS config for router " + router.getHostname();
    }
}
