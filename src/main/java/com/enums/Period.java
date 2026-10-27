package com.enums;

public enum Period {
    MORNING(0, "Manhã"), AFTERNOON(1, "Tarde"), NIGHT(2, "Noite");

    private Integer code;
    private String description;

    Period(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
