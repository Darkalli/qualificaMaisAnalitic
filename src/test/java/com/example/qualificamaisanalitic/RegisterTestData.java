package com.example.qualificamaisanalitic;

import com.sheets.RegisterSheetMapper;
import com.entities.Register;
import java.util.List;

public final class RegisterTestData {
    private RegisterTestData() { }

    public static Register register(String cpf) {
        return register(cpf, 1L);
    }

    public static Register register(String cpf, Long courseId) {
        return new RegisterSheetMapper().map(List.of(
                List.of("Nome completo", "CPF", "E-mail", "Telefone pessoal", "Possui WhatsApp",
                        "Rua", "Número", "Bairro", "Gênero", "Escolaridade", "Situação de trabalho",
                        "Deficiência", "ID do curso", "Data de cadastro"),
                List.of("Pessoa Exemplo", cpf, "pessoa@example.com", "11999990000", "Não",
                        "Rua Exemplo", "42", "Centro", "Feminino", "Ensino Médio Completo",
                        "Não, somente estudo", "Auditiva, Visual", courseId.toString(), "25/09/2026")), 1)
                .registers().getFirst();
    }
}
