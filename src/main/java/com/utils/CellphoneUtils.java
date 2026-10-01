package com.utils;

public class CellphoneUtils {

    public static String formatPhone(String phone) {
        String normalized = cleanPhone(phone);
        if (normalized == null) {
            return null;
        }

        if (normalized.length() == 10) {
            return "(" + normalized.substring(0, 2) + ") " + normalized.substring(2, 6) + "-" + normalized.substring(6, 10);
        }

        return "(" + normalized.substring(0, 2) + ") " + normalized.substring(2, 7) + "-" + normalized.substring(7, 11);
    }

    public static String cleanPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = phone.strip().replaceAll("[\\s().-]", "");

        if (!value.matches("\\+?[0-9]+")) {
            throw new IllegalArgumentException("Telefone contém caracteres inválidos.");
        }

        boolean international = value.startsWith("+");
        String digits = international ? value.substring(1) : value;
        boolean hasCountryCode = digits.startsWith("55")
                && (digits.length() == 12 || digits.length() == 13);

        if (international && !hasCountryCode) {
            throw new IllegalArgumentException("Use o prefixo +55 seguido do DDD e número.");
        }
        if (hasCountryCode) {
            digits = digits.substring(2);
        }
        if (!digits.matches("[1-9][0-9]{9,10}")) {
            throw new IllegalArgumentException("Informe 10 ou 11 dígitos, com DDD sem zero inicial.");
        }

        return digits;
    }
}
