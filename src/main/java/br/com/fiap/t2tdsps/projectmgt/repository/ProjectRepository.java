package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Project;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class ProjectRepository {
    private Map<Long, Project> database = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public List<Project> findAll() {
        return database.values().stream().toList();
    }

    public Optional<Project> findById(Long id) {
        return Optional.ofNullable(database.get(id));
    }

    public Project save(Project project) {
        if (project.getId() == null) {
            project.setId(sequence.incrementAndGet());
        }
        database.put(project.getId(), project);
        return project;
    }

    public void deleteById(Long id) {
        database.remove(id);
    }

    public boolean existsById(Long id) {
        return database.containsKey(id);
    }
}
