package br.com.fiap.t2tdsps.projectmgt.configurations;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import br.com.fiap.t2tdsps.projectmgt.repository.CollaboratorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Padrão seeder
// O cadastro público só cria MEMBER, então alguém precisa nascer ADMIN: este seed (só se ainda não existir).
@Component
public class AdminInitializer implements ApplicationRunner {

    //idealmente se for envolver população de dados que tem regras de negocio usar o service ao inves da repository
    private final CollaboratorRepository collaboratorRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminInitializer(CollaboratorRepository collaboratorRepository, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email}") String adminEmail,
                            @Value("${app.admin.password}") String adminPassword) {
        this.collaboratorRepository = collaboratorRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (collaboratorRepository.existsByEmail(adminEmail)) {
            return;
        }
        Collaborator admin = new Collaborator();
        admin.setName("Administrador");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        collaboratorRepository.save(admin);
    }
}
