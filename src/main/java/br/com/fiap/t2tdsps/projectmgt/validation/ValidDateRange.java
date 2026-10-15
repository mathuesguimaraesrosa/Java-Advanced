package br.com.fiap.t2tdsps.projectmgt.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Constraint(validatedBy = {DataRangeValidator.class})
@Target({TYPE})
@Retention(RUNTIME)
public @interface ValidDateRange {

    String message() default "data de inicio e fim estão nulas ou inválidas";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
