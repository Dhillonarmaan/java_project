package com.keybridge.controller;

import com.keybridge.audit.Audited;
import com.keybridge.dto.dsc.CreateDscApplicationRequest;
import com.keybridge.dto.dsc.DscApplicationResponse;
import com.keybridge.dto.dsc.DscRenewalReminder;
import com.keybridge.dto.dsc.UpdateChecklistItemRequest;
import com.keybridge.dto.dsc.UpdateDscStatusRequest;
import com.keybridge.entity.User;
import com.keybridge.service.AuthService;
import com.keybridge.service.DscApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dsc-applications")
public class DscApplicationController {

    private final DscApplicationService applicationService;
    private final AuthService authService;

    public DscApplicationController(DscApplicationService applicationService, AuthService authService) {
        this.applicationService = applicationService;
        this.authService = authService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Audited("DSC_APPLY")
    public ResponseEntity<DscApplicationResponse> create(@Valid @RequestBody CreateDscApplicationRequest dto) {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(dto, currentUser));
    }

    @GetMapping
    public ResponseEntity<List<DscApplicationResponse>> list() {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(applicationService.list(currentUser));
    }

    @GetMapping("/reminders")
    public ResponseEntity<List<DscRenewalReminder>> renewalReminders() {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(applicationService.renewalReminders(currentUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DscApplicationResponse> get(@PathVariable Long id) {
        User currentUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(applicationService.get(id, currentUser));
    }

    @PutMapping("/{id}/documents")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited("DSC_DOCUMENT_UPDATE")
    public ResponseEntity<DscApplicationResponse> updateChecklistItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateChecklistItemRequest dto
    ) {
        User adminUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(applicationService.updateChecklistItem(id, dto, adminUser));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Audited("DSC_STATUS_UPDATE")
    public ResponseEntity<DscApplicationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDscStatusRequest dto
    ) {
        User adminUser = authService.getCurrentUserEntity();
        return ResponseEntity.ok(applicationService.updateStatus(id, dto, adminUser));
    }
}
