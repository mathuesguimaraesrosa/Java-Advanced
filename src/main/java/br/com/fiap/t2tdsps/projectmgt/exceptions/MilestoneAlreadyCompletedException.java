package br.com.fiap.t2tdsps.projectmgt.exceptions;

public class MilestoneAlreadyCompletedException extends RuntimeException {

    public MilestoneAlreadyCompletedException(Long id) {
        super("Milestone com id " + id + " já está concluída e não pode ser alterada ou removida");
    }
}
