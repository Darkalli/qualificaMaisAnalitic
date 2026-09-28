package com.enums;

public enum PresenceStatus {

    PRESENT(0, "Presente"), ABSENT(1,"Ausente"),
    JUSTIFIED(2, "Justificado");

    private Integer code;
    private String description;

    PresenceStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static PresenceStatus toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (PresenceStatus p : PresenceStatus.values()) {
                if (code.equals(p.getCode())) {
                    return p;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
