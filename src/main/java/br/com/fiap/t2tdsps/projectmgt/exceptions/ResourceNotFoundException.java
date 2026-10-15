package br.com.fiap.t2tdsps.projectmgt.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " com id "+id+" não encontrado");
    }
}
