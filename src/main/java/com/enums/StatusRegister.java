package com.enums;

public enum StatusRegister {

    ACTIVE(0, "Ativa"), CANCELED(1,"Cancelada");
    private Integer code;
    private String description;

    StatusRegister(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static StatusRegister toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (StatusRegister s : StatusRegister.values()) {
                if (code.equals(s.getCode())) {
                    return s;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
