package br.com.fiap.t2tdsps.projectmgt.exceptions;

public class EmailAlreadyRegisteredException extends RuntimeException {
	
	public EmailAlreadyRegisteredException(String message) {
		super(message);
	}
}
