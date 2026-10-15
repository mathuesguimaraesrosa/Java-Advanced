package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollaboratorRepository extends JpaRepository<Collaborator, Long> {

    Optional<Collaborator> findByEmail(String email);

    boolean existsByEmail(String email);
}
