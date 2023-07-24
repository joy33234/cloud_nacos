package com.seektop.common.validator.in;

import org.apache.commons.lang3.ObjectUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.stream.Stream;

public class CheckStringValidator implements ConstraintValidator<InValidator, Object>

{

    private Object [] array;

    @Override
    public void initialize(InValidator constraintAnnotation) {
       array = constraintAnnotation.mustIn();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext constraintValidatorContext) {
        // 空参数值 或 校验值 校验通过
        if (ObjectUtils.isEmpty(value) || array.length == 0) {
            return true;
        }
        return Stream.of(array).anyMatch(a-> a.equals(String.valueOf(value)));
    }


}