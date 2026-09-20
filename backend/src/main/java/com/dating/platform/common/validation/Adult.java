package com.dating.platform.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/** Legal-age gate. Applied to every date of birth that enters the system. */
@Documented
@Constraint(validatedBy = AdultValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Adult {

    String message() default "You must be at least 18 years old";

    int minAge() default 18;

    int maxAge() default 120;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
