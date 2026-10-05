package com.utils;

import com.entities.Address;
import java.time.LocalDate;

public final class ValidationUtils {
    private ValidationUtils() {
    }

    public static void required(Object value, String field) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException("O campo '" + field + "' é obrigatório.");
        }
    }

    public static void positiveId(Long value, String field) {
        required(value, field);
        if (value <= 0) {
            throw new IllegalArgumentException("O campo '" + field + "' deve ser um inteiro positivo.");
        }
    }

    public static void maxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException("O campo '" + field + "' deve ter no máximo " + max + " caracteres.");
        }
    }

    public static void email(String value) {
        required(value, "email");
        if (!value.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) {
            throw new IllegalArgumentException("O campo 'email' deve conter um endereço de e-mail válido.");
        }
    }

    public static void address(Address address) {
        required(address, "address");
        required(address.getStreet(), "address.street");
        required(address.getNeighborhood(), "address.neighborhood");
        required(address.getNumber(), "address.number");
        if (address.getNumber() < 0) {
            throw new IllegalArgumentException("O campo 'address.number' deve ser um inteiro maior ou igual a zero.");
        }
    }

    public static void dateRange(LocalDate start, LocalDate finish) {
        if (start != null && finish != null && start.isAfter(finish)) {
            throw new IllegalArgumentException("O campo 'finish' não pode ser anterior ao campo 'start'.");
        }
    }
}
