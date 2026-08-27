package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Project> findAll(){
        return this.projectRepository.findAll();
    }

    public Optional<Project> findById(Long id) {
        return this.projectRepository.findById(id);
    }

    public Project create(Project project) {
        project.setId(null); // garante que o cliente nunca escolhe o próprio id
        return this.projectRepository.save(project);
    }

    public Optional<Project> update(Long id, Project updated) {
        return this.projectRepository.findById(id).map(existing -> {
            updated.setId(id);
            return projectRepository.save(updated);
        });
    }

    public boolean delete(Long id) {
        if (!this.projectRepository.existsById(id)) {
            return false;
        }
        this.projectRepository.deleteById(id);
        return true;
    }

}
