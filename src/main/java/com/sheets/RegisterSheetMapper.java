package com.sheets;

import com.entities.Address;
import com.entities.Register;
import com.enums.Disabilities;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class RegisterSheetMapper {
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter BR_TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    public SheetImportResult map(List<List<Object>> rows, int headerRow) {
        if (headerRow < 1) {
            throw new IllegalArgumentException("A linha do cabeçalho deve ser maior que zero.");
        }
        if (rows == null || rows.isEmpty()) {
            return new SheetImportResult(List.of(), List.of(), 0);
        }
        Map<Column, Integer> columns = readHeader(rows.getFirst());
        var registers = new ArrayList<Register>();
        var errors = new ArrayList<SheetImportResult.RowError>();
        int ignored = 0;
        for (int index = 1; index < rows.size(); index++) {
            List<Object> row = rows.get(index);
            if (row == null || row.stream().allMatch(value -> value == null || value.toString().isBlank())) {
                ignored++;
                continue;
            }
            try {
                registers.add(readRegister(row, columns));
            } catch (IllegalArgumentException exception) {
                // Não inclui os dados pessoais da linha no relatório de erros.
                errors.add(new SheetImportResult.RowError(headerRow + index, exception.getMessage()));
            }
        }
        return new SheetImportResult(registers, errors, ignored);
    }

    private Map<Column, Integer> readHeader(List<Object> header) {
        Map<Column, Integer> columns = new HashMap<>();
        Integer timestampIndex = null;
        if (header == null) {
            throw new IllegalArgumentException("Cabeçalho ausente.");
        }
        for (int index = 0; index < header.size(); index++) {
            String title = normalize(header.get(index) == null ? "" : header.get(index).toString());
            if (title.equals(normalize("Carimbo de data/hora"))) {
                if (timestampIndex != null) {
                    throw new IllegalArgumentException("Coluna duplicada: Carimbo de data/hora");
                }
                timestampIndex = index;
                continue;
            }
            for (Column column : Column.values()) {
                if (column.matches(title) && columns.putIfAbsent(column, index) != null) {
                    throw new IllegalArgumentException("Coluna duplicada: " + column.label);
                }
            }
        }
        // A data declarada da inscrição prevalece sobre o horário de envio do formulário.
        if (timestampIndex != null) {
            columns.putIfAbsent(Column.REGISTER_DATE, timestampIndex);
        }
        var missing = new ArrayList<String>();
        for (Column column : Column.values()) {
            if (column.required && !columns.containsKey(column)) {
                missing.add(column.label);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Colunas obrigatórias ausentes: " + String.join(", ", missing));
        }
        return columns;
    }

    private Register readRegister(List<Object> row, Map<Column, Integer> columns) {
        var register = new Register();
        register.setFullName(cell(row, columns, Column.FULL_NAME));
        register.setSocialName(cell(row, columns, Column.SOCIAL_NAME));
        String cpf = cell(row, columns, Column.CPF);
        if (!cpf.matches("[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}")) {
            throw new IllegalArgumentException("CPF: informe 11 dígitos, com ou sem a máscara 000.000.000-00.");
        }
        register.setCpf(cpf.replaceAll("[.-]", ""));
        register.setEmail(cell(row, columns, Column.EMAIL));
        register.setPersonalPhone(phone(cell(row, columns, Column.PERSONAL_PHONE), Column.PERSONAL_PHONE));
        register.setPersonalPhoneHasWhatsapp(whatsapp(cell(row, columns, Column.PERSONAL_PHONE_HAS_WHATSAPP)));
        register.setFamilyPhone(phone(cell(row, columns, Column.FAMILY_PHONE), Column.FAMILY_PHONE));
        var address = new Address();
        address.setStreet(cell(row, columns, Column.STREET));
        address.setNeighborhood(cell(row, columns, Column.NEIGHBORHOOD));
        try {
            int number = Integer.parseInt(cell(row, columns, Column.NUMBER));
            if (number < 0) {
                throw new NumberFormatException();
            }
            address.setNumber(number);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Número: informe um inteiro não negativo.");
        }
        register.setAddress(address);
        register.setGender(enumValue(cell(row, columns, Column.GENDER), Gender.values(),
                Gender::getCode, Gender::getDescription, Column.GENDER));
        register.setEducation(enumValue(cell(row, columns, Column.EDUCATION), Education.values(),
                Education::getCode, Education::getDescription, Column.EDUCATION));
        register.setWorkState(enumValue(cell(row, columns, Column.WORK_STATE), WorkState.values(),
                WorkState::getCode, WorkState::getDescription, Column.WORK_STATE));
        register.setDisabilities(disabilities(cell(row, columns, Column.DISABILITIES)));
        register.setCourseOfInterest(cell(row, columns, Column.COURSE));
        register.setRegisterDate(date(cell(row, columns, Column.REGISTER_DATE)));
        return register;
    }

    private String cell(List<Object> row, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        String value = index == null || index >= row.size() || row.get(index) == null
                ? "" : row.get(index).toString().strip();
        if (value.isBlank()) {
            if (column.required) {
                throw new IllegalArgumentException(column.label + ": preenchimento obrigatório.");
            }
            return null;
        }
        return value;
    }

    private Set<Disabilities> disabilities(String value) {
        var selected = EnumSet.noneOf(Disabilities.class);
        // A barra de Física/Motora faz parte da descrição, não é um separador.
        for (String item : value.split(",|;|\\R", -1)) {
            selected.add(enumValue(item.strip(), Disabilities.values(),
                    Disabilities::getCode, Disabilities::getDescription, Column.DISABILITIES));
        }
        if (selected.size() > 1 && (selected.contains(Disabilities.NONE)
                || selected.contains(Disabilities.NO_DECLARATION))) {
            throw new IllegalArgumentException("Deficiência: Nenhuma e Sem Declaração devem ser informadas isoladamente.");
        }
        return selected;
    }

    private String phone(String value, Column column) {
        if (value == null) {
            return null;
        }
        // Mantém telefone como texto; aceita máscara e o prefixo explícito +55.
        String digits = value.replaceAll("[\\s().-]", "");
        if (digits.startsWith("+55")) {
            digits = digits.substring(3);
        }
        if (!digits.matches("[1-9][0-9]{9,10}")) {
            throw new IllegalArgumentException(column.label + ": informe um telefone com DDD, de 10 ou 11 dígitos.");
        }
        return digits;
    }

    private Boolean whatsapp(String value) {
        return switch (normalize(value)) {
            case "sim", "true" -> true;
            case "nao", "false" -> false;
            default -> throw new IllegalArgumentException("O contato informado possui WhatsApp?: informe Sim ou Não.");
        };
    }

    private <E extends Enum<E>> E enumValue(String value, E[] options, Function<E, Integer> code,
                                            Function<E, String> description, Column column) {
        String normalized = normalize(value);
        for (E option : options) {
            if (normalized.equals(normalize(option.name()))
                    || normalized.equals(normalize(description.apply(option)))
                    || value.equals(code.apply(option).toString())) {
                return option;
            }
        }
        throw new IllegalArgumentException(column.label + ": valor não reconhecido; use a descrição, o nome ou o código do enum.");
    }

    private LocalDate date(String value) {
        for (DateTimeFormatter formatter : List.of(BR_DATE, DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Tenta os formatos de data/hora usados pelo formulário.
            }
        }
        for (DateTimeFormatter formatter : List.of(BR_TIMESTAMP, DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try {
                return LocalDateTime.parse(value, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // Tenta o próximo formato sem aceitar datas inválidas.
            }
        }
        throw new IllegalArgumentException("Data de cadastro: use dd/MM/aaaa, dd/MM/aaaa HH:mm:ss ou uma data ISO.");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private enum Column {
        FULL_NAME(true, "Nome completo", "fullName", "Nome"),
        SOCIAL_NAME(false, "Nome social", "socialName"),
        CPF(true, "CPF"),
        EMAIL(true, "E-mail", "email", "Endereço de e-mail"),
        PERSONAL_PHONE(true, "Telefone pessoal", "personalPhone", "Contato com WhatsApp", "Telefone para contato"),
        PERSONAL_PHONE_HAS_WHATSAPP(true, "O contato informado possui WhatsApp?", "personalPhoneHasWhatsapp", "Possui WhatsApp"),
        FAMILY_PHONE(false, "Contato de familiar", "familyPhone", "Telefone de familiar"),
        STREET(true, "Rua", "street", "Logradouro", "Endereço (rua)"),
        NUMBER(true, "Número", "number", "Número do endereço"),
        NEIGHBORHOOD(true, "Bairro", "neighborhood"),
        GENDER(true, "Gênero", "gender", "Sexo"),
        EDUCATION(true, "Escolaridade", "education"),
        WORK_STATE(true, "Situação de trabalho", "workState", "Situação profissional", "Trabalha atualmente?"),
        DISABILITIES(true, "Deficiência", "disabilities", "Deficiências"),
        COURSE(true, "Curso de interesse", "courseOfInterest"),
        REGISTER_DATE(true, "Data de cadastro", "registerDate", "Data de inscrição", "Data da inscrição");

        private final boolean required;
        private final String label;
        private final List<String> aliases;

        Column(boolean required, String label, String... aliases) {
            this.required = required;
            this.label = label;
            var titles = new ArrayList<String>();
            titles.add(normalize(label));
            for (String alias : aliases) {
                titles.add(normalize(alias));
            }
            this.aliases = List.copyOf(titles);
        }

        boolean matches(String title) {
            return aliases.contains(title);
        }
    }
}
