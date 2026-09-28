package com.enums;

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
}
