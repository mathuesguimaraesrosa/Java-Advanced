package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Task;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class TaskRepository {

    private final Map<Long, Task> database = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public List<Task> findAll() {
        return new ArrayList<>(database.values());
    }

    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(database.get(id));
    }

    public Task save(Task task) {
        if (task.getId() == null) {
            task.setId(sequence.incrementAndGet());
        }
        database.put(task.getId(), task);
        return task;
    }

    public void deleteById(Long id) {
        database.remove(id);
    }

    public boolean existsById(Long id) {
        return database.containsKey(id);
    }
}
