package com.example.licenta.Utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class YesNoBooleanConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return attribute != null && attribute ? "Yes" : "No";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Yes".equalsIgnoreCase(dbData);
    }
}