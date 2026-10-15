package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectDetailRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectDetailResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectWithMilestonesResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.ProjectResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.PageResponse;
import br.com.fiap.t2tdsps.projectmgt.mapper.ProjectDetailMapper;
import br.com.fiap.t2tdsps.projectmgt.mapper.ProjectMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectDetailService;
import br.com.fiap.t2tdsps.projectmgt.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Validated
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectDetailService projectDetailService;

    public ProjectController(ProjectService projectService, ProjectDetailService projectDetailService) {
        this.projectService = projectService;
        this.projectDetailService = projectDetailService;
    }

    //since em geral se coloca a versão
    @Deprecated(since = "Foi substituido pelo método que pagina na semana 4")
//    @GetMapping
    public List<ProjectResponse> getAllProjects(@RequestParam(required = false) ProjectStatus status) {
        List<Project> projects = status != null
                ? projectService.findByStatus(status)
                : projectService.findAll();

        return projects.stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    // Desafio resolvido: mapeamos Page<Project> -> Page<ProjectResponse> (Page.map preserva a paginação)
    // antes de embrulhar no PageResponse - sem isso, a entidade JPA vazava direto na resposta.
    // O filtro "status" também estava sendo ignorado (nunca era usado); agora ele decide qual busca paginada chamar.
    @GetMapping
    public PageResponse<ProjectResponse> getAllProjects(@RequestParam(required = false) ProjectStatus status,
                                                @RequestParam(required = false,defaultValue = "0") Integer page,
                                                @RequestParam(required = false,defaultValue = "10") Integer sizePage){
        Page<Project> pageResult = status != null
                ? this.projectService.findByStatus(status, page, sizePage)
                : this.projectService.findAll(page, sizePage);

        return PageResponse.from(pageResult.map(ProjectMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable @Positive Long id) {
//        return projectService.findById(id)
//                .map(ProjectMapper::toResponse)
//                .map(ResponseEntity::ok)
//                .orElseGet(() -> ResponseEntity.notFound().build());

        return ResponseEntity.ok(
                ProjectMapper.toResponse( //mapeando para dto
                        projectService.findById(id) // objeto retornando da busca ao banco
                )
        );
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody @Valid ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(ProjectStatus.PLANEJADO); // toda criação começa como PLANEJADO - regra da própria entidade

        Project salvo = projectService.create(project);
        return ResponseEntity.created(
                URI.create("/api/projects/" + salvo.getId()))
                .body(ProjectMapper.toResponse(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable Long id, @RequestBody ProjectRequest request) {
        // Mutamos o objeto já gerenciado, sem tocar em "status": o PUT aqui não é dono
        // do ciclo de vida do projeto (isso é papel do setStatus, numa operação à parte).
        Project project =projectService.findById(id);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());

        Project updated = projectService.update(id, project).orElseThrow();
        return ResponseEntity.ok(ProjectMapper.toResponse(updated));
    }

    // Endpoint didático (@EntityGraph): mesma resposta, duas estratégias de carga. Compare o SQL no console
    // (show-sql: true):
    //   ?graph=false -> findById + getMilestones() no mapper: DOIS selects (project, depois milestone)
    //   ?graph=true  -> findWithMilestonesById: UM select com left join milestone
    @GetMapping("/{projectId}/with-milestones")
    public ResponseEntity<ProjectWithMilestonesResponse> findWithMilestones(
            @PathVariable @Positive Long projectId,
            @RequestParam(required = false, defaultValue = "true") boolean graph) {
        Project project = graph
                ? projectService.findByIdWithMilestones(projectId)
                : projectService.findById(projectId);
        return ResponseEntity.ok(ProjectMapper.toResponseWithMilestones(project));
    }

    @GetMapping("/{projectId}/detail")
    public ResponseEntity<ProjectDetailResponse> findDetail(@PathVariable @Positive Long projectId) {
        return ResponseEntity.ok(ProjectDetailMapper.toResponse(projectDetailService.findByProjectId(projectId)));
    }

    // PUT idempotente: cria o detalhe se ainda não existir, senão atualiza (1:1 - nunca duplica).
    @PutMapping("/{projectId}/detail")
    public ResponseEntity<ProjectDetailResponse> createOrUpdateDetail(@PathVariable @Positive Long projectId,
                                                                      @RequestBody @Valid ProjectDetailRequest request) {
        ProjectDetail detail = new ProjectDetail();
        detail.setBudget(request.budget());
        detail.setClient(request.client());
        detail.setNotes(request.notes());

        return ResponseEntity.ok(ProjectDetailMapper.toResponse(projectDetailService.createOrUpdate(projectId, detail)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        this.projectService.delete(id); // lança ResourceNotFoundException se não existir
        return ResponseEntity.noContent().build();
    }

}
