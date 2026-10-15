package br.com.fiap.t2tdsps.projectmgt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// A entidade implementa UserDetails para o curso ficar enxuto. Em produção é comum separar: uma entidade
// de domínio e um adaptador de segurança (ex.: CollaboratorPrincipal) que a envolve.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Collaborator implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // "ROLE_" é o prefixo que hasRole('ADMIN') espera: hasRole('ADMIN') == hasAuthority('ROLE_ADMIN')
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
