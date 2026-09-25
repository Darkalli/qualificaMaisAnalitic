package com.sheets.enums;

public enum Education {

    ELEMENTARY_SCHOOL_INCOMPLETE(0, "Ensino Fundamental Incompleto"),
    ELEMENTARY_SCHOOL_IN_PROGRESS(1, "Ensino Fundamental Cursando"),
    ELEMENTARY_SCHOOL_COMPLETE(2, "Ensino Fundamental Completo"),
    HIGH_SCHOOL_INCOMPLETE(3, "Ensino Médio Incompleto"),
    HIGH_SCHOOL_IN_PROGRESS(4, "Ensino Médio Cursando"),
    HIGH_SCHOOL_COMPLETE(5, "Ensino Médio Completo"),
    HIGHER_EDUCATION_INCOMPLETE(6, "Ensino Superior Incompleto"),
    HIGHER_EDUCATION_IN_PROGRESS(7, "Ensino Superior Cursando"),
    HIGHER_EDUCATION_COMPLETE(8, "Ensino Superior Completo"),
    POSTGRADUATE_INCOMPLETE(9, "Pós-Graduação Incompleto"),
    POSTGRADUATE_IN_PROGRESS(10, "Pós-Graduação Cursando"),
    POSTGRADUATE_COMPLETE(11, "Pós-Graduação Completo");

    private Integer code;
    private String description;

    Education(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Education toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (Education e : Education.values()) {
                if (code.equals(e.getCode())) {
                    return e;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
