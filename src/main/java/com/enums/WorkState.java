package com.enums;

public enum WorkState {

    EMPLOYEE_WITH_SIGNED_WORK_CARD(0, "Sim, sou empregado(a) com carteira assinada"),
    EMPLOYEE_WITHOUT_SIGNED_WORK_CARD(1, "Sim, sou empregado(a) sem carteira assinada"),
    PUBLIC_EMPLOYEE(2, "Sim, sou funcionário(a) público(a)"),
    SELF_EMPLOYED_SERVICE_PROVIDER(3, "Sim, sou autônomo(a) prestador(a) de serviço"),
    SELF_EMPLOYED_PROFESSIONAL(4, "Sim, sou profissional liberal"),
    BUSINESS_OWNER(5, "Sim, tenho meu próprio negócio"),
    YES_OTHER(6, "Sim, outros"),
    ONLY_STUDYING(7, "Não, somente estudo"),
    NOT_INTERESTED_IN_WORKING(8, "Não, nem tenho interesse"),
    RETIRED(9, "Não, estou aposentado(a)"),
    HEALTH_PROBLEMS(10, "Não, tenho problemas de saúde"),
    DISMISSED(11, "Não, fui demitido(a)"),
    MILITARY_SERVICE(12, "Não, presto serviço militar"),
    NO_OTHER(13, "Não, outros");

    private Integer code;
    private String description;

    WorkState(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static WorkState toEnum(Integer code) throws IllegalAccessException {
        if(code == null){
            return null;
        }else {
            for (WorkState w : WorkState.values()) {
                if (code.equals(w.getCode())) {
                    return w;
                }
            }
        }
        throw new IllegalAccessException("Invalid Priority");
    }
}
