package br.com.fiap.t2tdsps.projectmgt.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 403 gerado na cadeia de filtros (ex.: requestMatchers(...).hasRole("ADMIN")): usuário autenticado, sem permissão.
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        JsonErrorWriter
                .write(
                        response,
                        HttpStatus.FORBIDDEN,
                        "Você não tem permissão para executar esta ação");
    }
}
