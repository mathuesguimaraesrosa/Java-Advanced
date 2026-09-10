package br.com.fiap.t2tdsps.projectmgt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    private Long id;
    private Long milestoneId;
    private String title;

    // Boolean (não boolean primitivo) por causa do ParameterNamesModule(PROPERTIES) que o Spring Boot
    // registra por padrão no Jackson: ele usa o construtor @AllArgsConstructor como creator e passa
    // `null` para qualquer campo ausente no JSON — o que quebra em tempo de desserialização para um
    // primitivo. O default "false" continua garantido no TaskService.create(), não aqui.
    private Boolean done;

    public Task(Long id) {
        this.id = id;
    }
}
