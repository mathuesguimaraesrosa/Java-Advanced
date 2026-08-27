package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MilestoneRepository {

    private final Map<Long, Milestone> database = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public List<Milestone> findAll() {
        return new ArrayList<>(database.values());
    }

    public Optional<Milestone> findById(Long id) {
        return Optional.ofNullable(database.get(id));
    }

    public Milestone save(Milestone milestone) {
        if (milestone.getId() == null) {
            milestone.setId(sequence.incrementAndGet());
        }
        database.put(milestone.getId(), milestone);
        return milestone;
    }

    public void deleteById(Long id) {
        database.remove(id);
    }

    public boolean existsById(Long id) {
        return database.containsKey(id);
    }
}