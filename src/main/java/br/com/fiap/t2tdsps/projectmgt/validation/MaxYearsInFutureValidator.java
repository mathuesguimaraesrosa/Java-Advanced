package br.com.fiap.t2tdsps.projectmgt.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class MaxYearsInFutureValidator implements ConstraintValidator<MaxYearsInFuture, LocalDate> {

    private int maxYears;

    @Override
    public void initialize(MaxYearsInFuture annotation) {
        this.maxYears = annotation.value();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull, se existir, é quem decide se null é aceitável
        }
        return !value.isAfter(LocalDate.now().plusYears(maxYears));
    }
}
