package br.com.fiap.t2tdsps.projectmgt.validation;

import br.com.fiap.t2tdsps.projectmgt.dto.ProjectRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DataRangeValidator implements ConstraintValidator<ValidDateRange, ProjectRequest> {
    @Override
    public boolean isValid(ProjectRequest value, ConstraintValidatorContext context) {
        return value.endDate()!= null &&
                value.startDate() != null &&
                value.endDate().isAfter(value.startDate());
    }
}
