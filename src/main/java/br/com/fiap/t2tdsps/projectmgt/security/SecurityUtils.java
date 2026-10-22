package br.com.fiap.t2tdsps.projectmgt.security;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    // O JwtAuthenticationFilter coloca o próprio Collaborator (UserDetails) como principal.
    public static Collaborator currentCollaborator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Collaborator collaborator)) {
            throw new IllegalStateException("Nenhum colaborador autenticado no contexto de segurança");
        }
        return collaborator;
    }
}
