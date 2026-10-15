package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Project;
import br.com.fiap.t2tdsps.projectmgt.model.ProjectStatus;
import br.com.fiap.t2tdsps.projectmgt.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
class ProjectServiceImpl implements ProjectService{

    private final ProjectRepository projectRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public List<Project> findAll(){
        return this.projectRepository.findAll();
    }

    //size = quantidade de itens por página
    // Exemplo pagina de 2 pagina, com paginas de ate 10 itens
    // OFFSET (pagina * quantiadade de itens por pagina)
    //vai fazer um query no db =>  select * from PROJECT OFFSET 10 LIMIT 10
    public Page<Project> findAll(Integer page, Integer size){
        return this.projectRepository.findAll(
                Pageable.ofSize(size).withPage(page)
        );
    }

    @Override
    public Page<Project> findByStatus(ProjectStatus status, Integer page, Integer size) {
        return this.projectRepository.findByStatus(
                status,
                Pageable.ofSize(size).withPage(page)
        );
    }

    
    public Project findById(Long id) {
        Optional<Project> byId = this.projectRepository.findById(id);
        if (!byId.isPresent()){
            throw new ResourceNotFoundException("projeto",id);
        }
        return byId.get();
//        Essa seria uma maneira mais rapida de chegar ao mesmo resultado, dispenseria todas as linhas acima desse metodo
//        return this.projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("projeto",id));
    }

    @Override
    public Project findByIdWithMilestones(Long id) {
        return this.projectRepository.findWithMilestonesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("projeto", id));
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

    public void delete(Long id) {
        Project project = findById(id); // garante que existe - lança ResourceNotFoundException se não
        this.projectRepository.delete(project);
    }

    @Override
    public List<Project> findByStatus(ProjectStatus status) {
        return this.projectRepository.findByStatus(status);
    }

    public void testeTiago(){

    }

}
