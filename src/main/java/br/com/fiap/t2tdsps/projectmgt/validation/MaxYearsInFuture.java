package br.com.fiap.t2tdsps.projectmgt.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Constraint(validatedBy = {MaxYearsInFutureValidator.class})
@Target({FIELD})
@Retention(RUNTIME)
public @interface MaxYearsInFuture {

    int value(); // quantidade máxima de anos no futuro - sem default, obrigatório informar

    String message() default "data não pode estar a mais de {value} anos no futuro";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
