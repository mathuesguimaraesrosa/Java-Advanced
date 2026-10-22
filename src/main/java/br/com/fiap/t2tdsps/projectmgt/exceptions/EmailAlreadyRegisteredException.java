package br.com.fiap.t2tdsps.projectmgt.exceptions;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Já existe um colaborador cadastrado com o email " + email);
    }
}
