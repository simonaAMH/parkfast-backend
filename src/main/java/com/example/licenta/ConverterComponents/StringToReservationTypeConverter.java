package com.example.licenta.ConverterComponents;

import com.example.licenta.Enum.Reservation.ReservationType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToReservationTypeConverter implements Converter<String, ReservationType> {
    @Override
    public ReservationType convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }
        return ReservationType.fromString(source.trim());
    }
}