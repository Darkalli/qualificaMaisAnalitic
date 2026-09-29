package com.utils;

public class CellphoneUtils {

    public static String formatPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String normalized = phone.replaceAll("\\D", "");

        if ((normalized.length() == 12 || normalized.length() == 13)
                && normalized.startsWith("55")) {
            normalized = normalized.substring(2);
        }

        if (normalized.length() == 10) {
            return "(" + normalized.substring(0, 2) + ") "
                    + normalized.substring(2, 6) + "-"
                    + normalized.substring(6, 10);
        }

        if (normalized.length() == 11) {
            return "(" + normalized.substring(0, 2) + ") "
                    + normalized.substring(2, 7) + "-"
                    + normalized.substring(7, 11);
        }

        throw new IllegalArgumentException(
                "Telefone deve conter 10 ou 11 dígitos, com DDD."
        );
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String normalized = phone.replaceAll("\\D", "");

        if ((normalized.length() == 12 || normalized.length() == 13)
                && normalized.startsWith("55")) {
            normalized = normalized.substring(2);
        }

        if (normalized.length() != 10 && normalized.length() != 11) {
            throw new IllegalArgumentException(
                    "Telefone deve conter 10 ou 11 dígitos, com DDD."
            );
        }

        return normalized;
    }
}