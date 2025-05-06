package com.example.licenta.Validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ParkingLotValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ParkingLotConstraint {
    String message() default "Invalid parking lot configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}