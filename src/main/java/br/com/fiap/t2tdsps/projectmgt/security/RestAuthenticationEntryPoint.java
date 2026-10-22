package br.com.fiap.t2tdsps.projectmgt.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

// 401: chega aqui quando a rota exige autenticação e NÃO há usuário autenticado (sem token, token inválido/expirado).
// Roda dentro da cadeia de filtros, antes do DispatcherServlet - por isso o @RestControllerAdvice não enxerga.
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        JsonErrorWriter.write(response,
                HttpStatus.UNAUTHORIZED,
                "Autenticação necessária: envie um token válido");
    }
}
