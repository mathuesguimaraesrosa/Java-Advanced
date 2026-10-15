package br.com.fiap.t2tdsps.projectmgt.exceptions;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
