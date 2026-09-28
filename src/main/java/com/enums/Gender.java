package com.enums;

public enum Gender {

    MALE(0, "Masculino"), FEMALE(1,"Feminino"),
    OTHER(2, "Outro");
    private Integer code;
    private String description;

    Gender(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Gender toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (Gender g : Gender.values()) {
                if (code.equals(g.getCode())) {
                    return g;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
