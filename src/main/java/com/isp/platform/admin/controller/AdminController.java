package com.isp.platform.admin.controller;

import com.isp.platform.admin.service.AdminService;
import com.isp.platform.admin.service.PopRequest;
import com.isp.platform.admin.service.RouterRequest;
import com.isp.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/pops")
    public ResponseEntity<ApiResponse<?>> createPop(@Valid @RequestBody PopRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.createPop(request)));
    }

    @PostMapping("/routers")
    public ResponseEntity<ApiResponse<?>> createRouter(@Valid @RequestBody RouterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.createRouter(request)));
    }

    @GetMapping("/routers")
    public ResponseEntity<ApiResponse<?>> listRouters() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listRouters()));
    }
}
