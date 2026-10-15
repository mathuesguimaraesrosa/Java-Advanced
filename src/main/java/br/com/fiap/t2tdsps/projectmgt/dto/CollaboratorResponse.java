package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.Role;

// Nunca devolver a entidade Collaborator: ela carrega o hash da senha.
public record CollaboratorResponse(Long id, String name, String email, Role role) {
}
