package br.com.fiap.t2tdsps.projectmgt.mapper;

import br.com.fiap.t2tdsps.projectmgt.dto.CollaboratorResponse;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;

public final class CollaboratorMapper {

    private CollaboratorMapper() {
    }

    public static CollaboratorResponse toResponse(Collaborator collaborator) {
        return new CollaboratorResponse(
                collaborator.getId(),
                collaborator.getName(),
                collaborator.getEmail(),
                collaborator.getRole()
        );
    }
}
