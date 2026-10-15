package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import br.com.fiap.t2tdsps.projectmgt.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import br.com.fiap.t2tdsps.projectmgt.model.Task;
import br.com.fiap.t2tdsps.projectmgt.repository.TaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TagService tagService;
    private final CollaboratorService collaboratorService;

    public TaskServiceImpl(TaskRepository taskRepository, TagService tagService,
                           CollaboratorService collaboratorService) {
        this.taskRepository = taskRepository;
        this.tagService = tagService;
        this.collaboratorService = collaboratorService;
    }

    @Override
    public Page<Task> findByMilestoneId(Long milestoneId, Integer page, Integer size) {
        return taskRepository.findByMilestoneId(milestoneId, Pageable.ofSize(size).withPage(page));
    }

    @Override
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    @Override
    public Task create(Task task) {
        task.setId(null); // garante que o cliente nunca escolhe o próprio id
        task.setDone(false); // toda task nasce não concluída
        return taskRepository.save(task);
    }

    @Override
    public void delete(Long id) {
        Task task = findById(id); // garante que existe - lança ResourceNotFoundException se não
        taskRepository.delete(task);
    }

    // Regra de posse: ADMIN e MANAGER concluem qualquer tarefa; MEMBER só a que foi atribuída a ele.
    // Fica no service (não no controller) porque depende do DADO (a tarefa), que só se conhece depois de carregá-la.
    @Override
    public Task markAsDone(Long id) {
        Task task = findById(id); // 404 antes de 403: quem não existe não tem dono
        Collaborator current = SecurityUtils.currentCollaborator();

        boolean isOwner = task.getAssignee() != null && task.getAssignee().getId().equals(current.getId());
        if (current.getRole() == Role.MEMBER && !isOwner) {
            throw new AccessDeniedException("Você só pode concluir tarefas atribuídas a você");
        }

        task.setDone(true);
        return taskRepository.save(task);
    }

    @Override
    public Task assign(Long taskId, Long collaboratorId) {
        Task task = findById(taskId);
        task.setAssignee(collaboratorService.findById(collaboratorId)); // 404 se o colaborador não existir
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task addTag(Long taskId, Long tagId) {
        Task task = findById(taskId);
        Tag tag = tagService.findById(tagId); // 404 se a tag não existir
        task.addTag(tag);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task removeTag(Long taskId, Long tagId) {
        Task task = findById(taskId);
        tagService.findById(tagId); // 404 se a tag não existir
        task.removeTag(tagId); // só some a linha de task_tag, a Tag continua
        return taskRepository.save(task);
    }
}
