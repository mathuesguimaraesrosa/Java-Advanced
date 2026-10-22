package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.EmailAlreadyRegisteredException;
import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.model.Role;
import br.com.fiap.t2tdsps.projectmgt.repository.CollaboratorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CollaboratorServiceImpl implements CollaboratorService {

    private final CollaboratorRepository collaboratorRepository;
    private final PasswordEncoder passwordEncoder;

    public CollaboratorServiceImpl(CollaboratorRepository collaboratorRepository, PasswordEncoder passwordEncoder) {
        this.collaboratorRepository = collaboratorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Collaborator register(String name, String email, String rawPassword) {
        if (collaboratorRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        Collaborator collaborator = new Collaborator();
        collaborator.setName(name);
        collaborator.setEmail(email);
        collaborator.setPassword(passwordEncoder.encode(rawPassword)); // BCrypt: só o hash vai para o banco
        collaborator.setRole(Role.MEMBER); // todo cadastro público nasce MEMBER
        return collaboratorRepository.save(collaborator);
    }

    @Override
    public Page<Collaborator> findAll(Integer page, Integer size) {
        return collaboratorRepository.findAll(Pageable.ofSize(size).withPage(page));
    }

    @Override
    public Collaborator findById(Long id) {
        return collaboratorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador", id));
    }

    @Override
    public Collaborator changeRole(Long id, Role role) {
        Collaborator collaborator = findById(id);
        collaborator.setRole(role);
        return collaboratorRepository.save(collaborator);
    }
}
