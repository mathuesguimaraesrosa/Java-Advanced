package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Task;
import br.com.fiap.t2tdsps.projectmgt.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    public Task create(Task task) {
        if (task.getTitle() == null || task.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title é obrigatório");
        }
        task.setId(null); // garante que o cliente nunca escolhe o próprio id
        task.setDone(false); // toda task nasce não concluída
        return taskRepository.save(task);
    }

    public boolean delete(Long id) {
        if (!taskRepository.existsById(id)) {
            return false;
        }
        taskRepository.deleteById(id);
        return true;
    }

    public Optional<Task> markAsDone(Long id) {
        return taskRepository.findById(id).map(task -> {
            task.setDone(true);
            return taskRepository.save(task);
        });
    }
}
