package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectDetailRepository extends JpaRepository<ProjectDetail, Long> {
    Optional<ProjectDetail> findByProjectId(Long projectId); // continua funcionando sem mudar quando project virar project.id
}
