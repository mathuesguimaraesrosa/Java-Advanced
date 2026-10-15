package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import org.springframework.data.domain.Page;

public interface CollaboratorService {

    Collaborator register(String name, String email, String rawPassword);

    Page<Collaborator> findAll(Integer page, Integer size);

    Collaborator findById(Long id);

    Collaborator changeRole(Long id, Role role);
}
