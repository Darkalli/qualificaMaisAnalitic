package com.enums;

import java.text.Normalizer;
import java.util.*;

public enum Disabilities {

    INTELLECTUAL(0, "Intelectual"), HEARING(1, "Auditiva"),
    MOTOR(3,"Fisica/Motora"), VISUAL(4, "Visual"),
    NO_DECLARATION(5, "Sem Declaração"), NONE(6, "Nenhuma");


    private Integer code;
    private String description;

    Disabilities(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Disabilities toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (Disabilities d : Disabilities.values()) {
                if (code.equals(d.getCode())) {
                    return d;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }

    public static Disabilities cleanDisabilities(String d) {
        if (d == null || d.trim().isEmpty()) {
            return null;
        }

        String normalizedSearch = normalize(d);

        for (Disabilities dis : Disabilities.values()) {
            if (normalize(dis.name()).equals(normalizedSearch)) {
                return dis;
            }
            if (normalize(dis.getDescription()).equals(normalizedSearch)) {
                return dis;
            }
            if (String.valueOf(dis.code).equals(normalizedSearch)) {
                return dis;
            }
        }

        throw new IllegalArgumentException("Nenhuma deficiência encontrada para: " + d);
    }

    private static String normalize(String v) {
        if (v == null) return "";
        return Normalizer.normalize(v, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    public static Set<Disabilities> processAndValidateDisabilities(List<String> rawDisabilities) {
        if (rawDisabilities == null || rawDisabilities.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Disabilities> cleanedSet = new LinkedHashSet<>();

        for (String raw : rawDisabilities) {
            Disabilities dis = cleanDisabilities(raw);
            if (dis != null) {
                cleanedSet.add(dis);
            }
        }
        boolean hasNoDisabilityOption = cleanedSet.contains(Disabilities.NONE)
                || cleanedSet.contains(Disabilities.NO_DECLARATION);

        if (hasNoDisabilityOption && cleanedSet.size() > 1) {
            throw new IllegalArgumentException(
                    "Não é permitido selecionar 'Nenhuma' ou 'Não Declarado' junto com outras deficiências."
            );
        }

        return cleanedSet;
    }
}
