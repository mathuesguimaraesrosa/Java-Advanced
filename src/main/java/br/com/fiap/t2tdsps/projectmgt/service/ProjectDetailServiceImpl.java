package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectDetailRepository;
import org.springframework.stereotype.Service;

@Service
public class ProjectDetailServiceImpl implements ProjectDetailService {

    private final ProjectDetailRepository projectDetailRepository;
    private final ProjectService projectService;

    public ProjectDetailServiceImpl(ProjectDetailRepository projectDetailRepository, ProjectService projectService) {
        this.projectDetailRepository = projectDetailRepository;
        this.projectService = projectService;
    }

    @Override
    public ProjectDetail findByProjectId(Long projectId) {
        return projectDetailRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectDetail do projeto", projectId));
    }

    @Override
    public ProjectDetail createOrUpdate(Long projectId, ProjectDetail newDetail) {
        Project project = projectService.findById(projectId); // 404 se o projeto não existir

        ProjectDetail detail = projectDetailRepository.findByProjectId(projectId)
                .orElseGet(ProjectDetail::new);

        detail.setProject(project);
        detail.setBudget(newDetail.getBudget());
        detail.setClient(newDetail.getClient());
        detail.setNotes(newDetail.getNotes());

        return projectDetailRepository.save(detail);
    }
}
