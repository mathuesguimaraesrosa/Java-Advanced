package br.com.fiap.t2tdsps.projectmgt.security;

import br.com.fiap.t2tdsps.projectmgt.model.Collaborator;
import br.com.fiap.t2tdsps.projectmgt.repository.CollaboratorRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

// O Spring Security chama loadUserByUsername tanto no login (AuthenticationManager) quanto no filtro JWT.
@Service
public class CollaboratorUserDetailsService implements UserDetailsService {

    private final CollaboratorRepository collaboratorRepository;


    public CollaboratorUserDetailsService(CollaboratorRepository collaboratorRepository) {
        this.collaboratorRepository = collaboratorRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Collaborator> user = this.collaboratorRepository.findByEmail(email);
        if(user.isPresent()) {
            return user.get();
        }
        throw new UsernameNotFoundException("Colaborador não encontrado: "+ email);
    }
}
