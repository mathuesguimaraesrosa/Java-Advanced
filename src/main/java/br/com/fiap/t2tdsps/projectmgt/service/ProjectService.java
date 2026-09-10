package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectRepository;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;
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

    public Project findById(Long id) {
    	Optional<Project> byId = this.projectRepository.findById(id);
        if(!byId.isPresent()) {
        	throw new ResourceNotFoundException("project", id);
        }
    	return byId.get();
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
    //size = qunatidade de items por pagina
    //Exemplo pagina de 2 pagina, com paginas de are 10 items
    // OFFSET (pagina * qunatidade de items por pagina)
    //vai fazer um query no db => select * from PROJECT OFFSET 10 limit 10
    public Page<Project> findAll(Integer page, Integer size){
    	return this.projectRepository.findAll(Pageable.ofSize(size).withPage(page));
    }
    
    public boolean delete(Long id) {
        if (!this.projectRepository.existsById(id)) {
            return false;
        }
        this.projectRepository.deleteById(id);
        return true;
    }

    public List<Project> findByStatus(ProjectStatus status) {
        return this.projectRepository.findByStatus(status);
    }

}
