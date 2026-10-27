package com.enums;

public enum StatusCourse {

    ACTIVE(0, "Ativo"), CANCELED(1,"Cancelado"),
    CLOSED(2, "Encerrado");
    private Integer code;
    private String description;

    StatusCourse(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static StatusCourse toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (StatusCourse s : StatusCourse.values()) {
                if (code.equals(s.getCode())) {
                    return s;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
