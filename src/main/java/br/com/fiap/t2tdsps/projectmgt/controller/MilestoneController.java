package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.assembler.MilestoneModelAssembler;
import br.com.fiap.t2tdsps.projectmgt.dto.MilestoneRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.MilestoneResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.MilestoneMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import br.com.fiap.t2tdsps.projectmgt.model.MilestoneStatus;
import br.com.fiap.t2tdsps.projectmgt.service.MilestoneService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/milestones")
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final MilestoneModelAssembler milestoneModelAssembler;

    public MilestoneController(MilestoneService milestoneService, MilestoneModelAssembler milestoneModelAssembler) {
        this.milestoneService = milestoneService;
        this.milestoneModelAssembler = milestoneModelAssembler;
    }

    @GetMapping
    public CollectionModel<EntityModel<MilestoneResponse>> findAll() {
        var milestones = milestoneService.findAll().stream()
                .map(milestoneModelAssembler::toModel)
                .toList();

        return CollectionModel.of(milestones, linkTo(methodOn(MilestoneController.class).findAll()).withSelfRel());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<MilestoneResponse>> findById(@PathVariable Long id) {
        return milestoneService.findById(id)
                .map(milestoneModelAssembler::toModel)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Exemplo usando programacao funcional para enriquecer com mais links
    //      return milestoneService.findById(id)
    //            .map(m -> {
    //        EntityModel<MilestoneResponse> model = milestoneModelAssembler.toModel(m);
    //        model.add(linkTo(methodOn(MilestoneController.class).update(m.getId(), request)).withRel("update"));
    //        return model;
    //    })
    //            .map(ResponseEntity::ok)
    //                .orElseGet(() -> ResponseEntity.notFound().build());

    @PostMapping
    public ResponseEntity<MilestoneResponse> create(@RequestBody MilestoneRequest request) {
        Milestone milestone = new Milestone();
        milestone.setProjectId(request.projectId());
        milestone.setName(request.name());
        milestone.setDueDate(request.dueDate());
        milestone.setStatus(MilestoneStatus.PENDENTE); // toda milestone nasce PENDENTE

        Milestone created = milestoneService.create(milestone);
        return ResponseEntity.created(URI.create("/api/milestones/" + created.getId()))
                .body(MilestoneMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MilestoneResponse> update(@PathVariable Long id, @RequestBody MilestoneRequest request) {
        Optional<Milestone> existing = milestoneService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Milestone milestone = existing.get();
        milestone.setProjectId(request.projectId());
        milestone.setName(request.name());
        milestone.setDueDate(request.dueDate());
        // status preservado, mesma lógica do Project: PUT aqui não mexe no ciclo de vida da milestone

        Milestone updated = milestoneService.update(id, milestone).orElseThrow();
        return ResponseEntity.ok(MilestoneMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (milestoneService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
