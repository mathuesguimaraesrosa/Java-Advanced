package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.ChangeRoleRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.CollaboratorResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.CollaboratorMapper;
import br.com.fiap.t2tdsps.projectmgt.mapper.PageResponse;
import br.com.fiap.t2tdsps.projectmgt.service.CollaboratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// Toda a área /api/collaborators/** é só ADMIN: regra por MATCHER no SecurityConfig, sem @PreAuthorize aqui.
@RestController
@RequestMapping("/api/collaborators")
@Validated
public class CollaboratorController {

    private final CollaboratorService collaboratorService;

    public CollaboratorController(CollaboratorService collaboratorService) {
        this.collaboratorService = collaboratorService;
    }

    @GetMapping
    public PageResponse<CollaboratorResponse> findAll(@RequestParam(required = false, defaultValue = "0") Integer page,
                                                      @RequestParam(required = false, defaultValue = "10") Integer sizePage) {
        return PageResponse.from(collaboratorService.findAll(page, sizePage).map(CollaboratorMapper::toResponse));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<CollaboratorResponse> changeRole(@PathVariable @Positive Long id,
                                                           @RequestBody @Valid ChangeRoleRequest request) {
        return ResponseEntity.ok(CollaboratorMapper.toResponse(collaboratorService.changeRole(id, request.role())));
    }
}
