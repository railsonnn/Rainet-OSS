package com.isp.platform.admin.service;

import com.isp.platform.common.exception.ApiException;
import com.isp.platform.gateway.tenant.TenantContext;
import com.isp.platform.provisioning.domain.Pop;
import com.isp.platform.provisioning.domain.PopRepository;
import com.isp.platform.provisioning.domain.Router;
import com.isp.platform.provisioning.domain.RouterRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final PopRepository popRepository;
    private final RouterRepository routerRepository;

    public AdminService(PopRepository popRepository, RouterRepository routerRepository) {
        this.popRepository = popRepository;
        this.routerRepository = routerRepository;
    }

    @Transactional
    public Pop createPop(PopRequest request) {
        // Tenant ID is automatically set by TenantEntityListener on persist
        requireTenant(); // Verify tenant context is set
        Pop pop = new Pop();
        pop.setName(request.name());
        pop.setCity(request.city());
        return popRepository.save(pop);
    }

    @Transactional
    public Router createRouter(RouterRequest request) {
        Pop pop = popRepository.findById(request.popId())
                .filter(p -> p.getTenantId().equals(requireTenant()))
                .orElseThrow(() -> new ApiException("POP not found"));

        Router router = new Router();
        router.setPop(pop);
        router.setHostname(request.hostname());
        router.setManagementAddress(request.managementAddress());
        router.setRouterOsVersion(request.routerOsVersion());
        router.setApiUsername(request.apiUsername());
        router.setApiPassword(request.apiPassword());
        return routerRepository.save(router);
    }

    @Transactional(readOnly = true)
    public List<Router> listRouters() {
        return routerRepository.findByTenantId(requireTenant());
    }

    private UUID requireTenant() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ApiException("Tenant not resolved");
        }
        return tenantId;
    }
}
