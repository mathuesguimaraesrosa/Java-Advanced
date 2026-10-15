package br.com.fiap.t2tdsps.projectmgt.dto;

import br.com.fiap.t2tdsps.projectmgt.model.Role;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, String email, Role role) {
}
