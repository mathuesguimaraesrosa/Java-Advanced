package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectDto;
import br.com.fiap.t2tdsps.projectmgt.mapper.ProjectMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectDto> getAllProjects() {
        return ProjectMapper.toDto(projectService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> findById(@PathVariable Long id) {
        Optional<Project> p = this.projectService.findById(id);
        if (p.isPresent()) {
            return ResponseEntity.ok(ProjectMapper.toDto(p.get()));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project) {
        Project salvo = this.projectService.create(project);
        return ResponseEntity.created(URI.create("/api/projects/" + salvo.getId())).body(salvo);
    }

    //pra casa Put e Delete  -> PutMapping e DeleteMapping
    //aula que vem vamos testar endpoints - vamos ver o que realmente precisou de gets e sets
    // como fazer valer a entidade rica e hateoas

}
