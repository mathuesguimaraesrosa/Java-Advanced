package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.TagRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.TagResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.TagMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import br.com.fiap.t2tdsps.projectmgt.service.TagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
@Validated
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagResponse> findAll() {
        return tagService.findAll().stream().map(TagMapper::toResponse).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @PostMapping
    public ResponseEntity<TagResponse> create(@RequestBody @Valid TagRequest request) {
        Tag tag = new Tag();
        tag.setName(request.name());

        Tag created = tagService.create(tag);
        return ResponseEntity.created(URI.create("/api/tags/" + created.getId()))
                .body(TagMapper.toResponse(created));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
