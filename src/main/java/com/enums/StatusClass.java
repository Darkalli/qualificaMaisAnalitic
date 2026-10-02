package com.enums;

public enum StatusClass {


    ACTIVE(0, "Ativa"), CANCELED(1,"Cancelada"),
    POSTPONED (2, "Adiada");
    private Integer code;
    private String description;

    StatusClass(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static StatusClass toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (StatusClass s : StatusClass.values()) {
                if (code.equals(s.getCode())) {
                    return s;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
