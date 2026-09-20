package com.dating.platform.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class AdultValidator implements ConstraintValidator<Adult, LocalDate> {

    private int minAge;
    private int maxAge;

    @Override
    public void initialize(Adult annotation) {
        this.minAge = annotation.minAge();
        this.maxAge = annotation.maxAge();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // NotNull is a separate concern
        }
        if (value.isAfter(LocalDate.now())) {
            return false;
        }
        int age = Period.between(value, LocalDate.now()).getYears();
        return age >= minAge && age <= maxAge;
    }
}
