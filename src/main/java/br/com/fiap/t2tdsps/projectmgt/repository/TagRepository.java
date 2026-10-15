package br.com.fiap.t2tdsps.projectmgt.repository;

import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
}
