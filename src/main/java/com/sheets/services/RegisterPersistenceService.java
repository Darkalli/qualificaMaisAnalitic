package com.sheets.services;

import com.sheets.RegisterImportResult;
import com.sheets.SheetImportResult;
import com.sheets.entities.Register;
import com.sheets.repositories.RegisterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class RegisterPersistenceService {
    private final RegisterRepository repository;

    public RegisterPersistenceService(RegisterRepository repository) {
        this.repository = repository;
    }

    /** Recebe somente os objetos novos e normalizados produzidos pelo mapper. */
    @Transactional
    public RegisterImportResult persist(SheetImportResult collection) {
        int inserted = 0;
        int unchanged = 0;
        var conflicts = new ArrayList<RegisterImportResult.Conflict>();
        for (Register incoming : collection.registers()) {
            if (incoming.getCpf() == null || !incoming.getCpf().matches("[0-9]{11}")) {
                throw new IllegalArgumentException("A persistência requer CPF normalizado com 11 dígitos.");
            }
            if (incoming.getId() != null || incoming.getAddress() == null
                    || incoming.getAddress().getId() != null) {
                throw new IllegalArgumentException("A importação requer um cadastro e endereço novos, sem IDs.");
            }
            var existing = repository.findByCpf(incoming.getCpf());
            if (existing.isEmpty()) {
                repository.save(incoming);
                inserted++;
            } else {
                var fields = differences(existing.get(), incoming);
                if (fields.isEmpty()) {
                    unchanged++;
                } else {
                    conflicts.add(new RegisterImportResult.Conflict(existing.get().getId(), fields));
                }
            }
        }
        // Falhas de banco revertem todo o lote; o agendamento poderá relê-lo na próxima execução.
        repository.flush();
        return new RegisterImportResult(inserted, unchanged, conflicts, collection.errors(), collection.ignoredRows());
    }

    private List<String> differences(Register saved, Register incoming) {
        var fields = new ArrayList<String>();
        compare(fields, "fullName", saved.getFullName(), incoming.getFullName());
        compare(fields, "socialName", saved.getSocialName(), incoming.getSocialName());
        compare(fields, "email", saved.getEmail(), incoming.getEmail());
        compare(fields, "personalPhone", saved.getPersonalPhone(), incoming.getPersonalPhone());
        compare(fields, "personalPhoneHasWhatsapp", saved.getPersonalPhoneHasWhatsapp(), incoming.getPersonalPhoneHasWhatsapp());
        compare(fields, "familyPhone", saved.getFamilyPhone(), incoming.getFamilyPhone());
        compare(fields, "address.street", saved.getAddress().getStreet(), incoming.getAddress().getStreet());
        compare(fields, "address.number", saved.getAddress().getNumber(), incoming.getAddress().getNumber());
        compare(fields, "address.neighborhood", saved.getAddress().getNeighborhood(), incoming.getAddress().getNeighborhood());
        compare(fields, "gender", saved.getGender(), incoming.getGender());
        compare(fields, "education", saved.getEducation(), incoming.getEducation());
        compare(fields, "workState", saved.getWorkState(), incoming.getWorkState());
        compare(fields, "disabilities", saved.getDisabilities(), incoming.getDisabilities());
        compare(fields, "courseOfInterest", saved.getCourseOfInterest(), incoming.getCourseOfInterest());
        compare(fields, "registerDate", saved.getRegisterDate(), incoming.getRegisterDate());
        return fields;
    }

    private void compare(List<String> fields, String field, Object saved, Object incoming) {
        if (!Objects.equals(saved, incoming)) {
            fields.add(field);
        }
    }
}
