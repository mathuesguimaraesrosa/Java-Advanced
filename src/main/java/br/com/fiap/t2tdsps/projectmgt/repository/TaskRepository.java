package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByMilestoneId(Long milestoneId, Pageable pageable);
}
