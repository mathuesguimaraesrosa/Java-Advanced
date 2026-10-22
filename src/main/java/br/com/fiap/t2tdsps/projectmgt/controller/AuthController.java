package br.com.fiap.t2tdsps.projectmgt.controller;

import br.com.fiap.t2tdsps.projectmgt.dto.AuthResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.CollaboratorResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.LoginRequest;
import br.com.fiap.t2tdsps.projectmgt.dto.RegisterRequest;
import br.com.fiap.t2tdsps.projectmgt.mapper.CollaboratorMapper;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.security.JwtService;
import br.com.fiap.t2tdsps.projectmgt.security.SecurityUtils;
import br.com.fiap.t2tdsps.projectmgt.service.CollaboratorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CollaboratorService collaboratorService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(CollaboratorService collaboratorService, AuthenticationManager authenticationManager,
                          JwtService jwtService) {
        this.collaboratorService = collaboratorService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<CollaboratorResponse> register(@RequestBody @Valid RegisterRequest request) {
        Collaborator created = collaboratorService.register(request.name(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/collaborators/" + created.getId()))
                .body(CollaboratorMapper.toResponse(created));
    }

    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        // authenticate() consulta o UserDetailsService, compara a senha com o hash BCrypt e, se falhar,
        // lança BadCredentialsException (tratada no GlobalExceptionHandler como 401).
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        Collaborator collaborator = (Collaborator) authentication.getPrincipal();
        String token = jwtService.generateToken(collaborator);

        return ResponseEntity.ok(
                new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(),
                collaborator.getEmail(), collaborator.getRole()));
    }

    // Rota protegida (cai em anyRequest().authenticated()): devolve quem é o dono do token.
    @GetMapping("/me")
    public ResponseEntity<CollaboratorResponse> me() {
        return ResponseEntity.ok(CollaboratorMapper.toResponse(SecurityUtils.currentCollaborator()));
    }
}
