package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.PageResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.ProjectMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }
    @Deprecated(since = "Foi substituido pelo metodo de pagina semana 4")
    //@GetMapping()
    public List<ProjectResponse> getAllProjects(@RequestParam(required = false) ProjectStatus status) {
        List<Project> projects = status != null
                ? projectService.findByStatus(status)
                : projectService.findAll();

        return projects.stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }
    
    //Desafio: Sem alterar o PageResponse que criamos, retornar uma PageResponse<ProjectResponse>
    @GetMapping
    public PageResponse<Project> getAll(@RequestParam(required = false) ProjectStatus status,
    							@RequestParam(required = false, defaultValue = "0") Integer page, 
    							@RequestParam(required = false, defaultValue = "10") Integer sizePage){
    	return PageResponse.from(this.projectService.findAll(page, sizePage));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ProjectMapper.toResponse(projectService.findById(id)));
    	 
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(ProjectStatus.PLANEJADO); // toda criação começa como PLANEJADO - regra da própria entidade

        Project salvo = projectService.create(project);
        return ResponseEntity.created(URI.create("/api/projects/" + salvo.getId())).body(ProjectMapper.toResponse(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @RequestBody ProjectRequest request) {

        // Mutamos o objeto já gerenciado, sem tocar em "status": o PUT aqui não é dono
        // do ciclo de vida do projeto (isso é papel do setStatus, numa operação à parte).
        Project project = projectService.findById(id);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());

        Project updated = projectService.update(id, project).orElseThrow();
        return ResponseEntity.ok(ProjectMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        if (this.projectService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

}
