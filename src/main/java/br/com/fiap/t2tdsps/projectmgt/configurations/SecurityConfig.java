package br.com.fiap.t2tdsps.projectmgt.configurations;

import br.com.fiap.t2tdsps.projectmgt.security.JwtAuthenticationFilter;
import br.com.fiap.t2tdsps.projectmgt.security.RestAccessDeniedHandler;
import br.com.fiap.t2tdsps.projectmgt.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// habilita o uso de anotacoes que me permitem validar roles por endpoint ( @PreAuthorize nos controllers/services)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RestAccessDeniedHandler accessDeniedHandler,
                                           RestAuthenticationEntryPoint authenticationEntryPoint,
                                           JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                                .requestMatchers("/h2-console/**").permitAll()
                                .requestMatchers("/v3/api-docs/**" , "/swagger-ui/**" , "/swagger-ui.html").permitAll()
                                .requestMatchers("/api/collaborators/**").hasRole("ADMIN")
                                .anyRequest().authenticated()
                )
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(authenticationEntryPoint) //401
                                .accessDeniedHandler(accessDeniedHandler) // 403
                )
                // apenas por conta do H2 - só usar em dev
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                // injetando os nossos filters
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // para permitir o uso do AuthenticationManager na controller de autenticacao do usuario
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }


//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http,
//                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
//                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
//                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
//        http
//                // CSRF protege sessões baseadas em cookie; com token no header Authorization e sem sessão, não se aplica.
//                .csrf(csrf -> csrf.disable())
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//                .authorizeHttpRequests(auth -> auth
//                        // Regras GROSSAS (por área da URL) ficam aqui; regras finas (por ação) ficam em @PreAuthorize.
//                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
//                        .requestMatchers("/h2-console/**").permitAll() // só em desenvolvimento
//                        .requestMatchers("/api/collaborators/**").hasRole("ADMIN")
//                        .anyRequest().authenticated())
//                .exceptionHandling(ex -> ex
//                        .authenticationEntryPoint(authenticationEntryPoint) // 401
//                        .accessDeniedHandler(accessDeniedHandler))          // 403
//                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 console usa <iframe>
//                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }
}
