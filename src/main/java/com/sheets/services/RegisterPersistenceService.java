package com.sheets.services;

import com.sheets.RegisterImportResult;
import com.sheets.SheetImportResult;
import com.entities.Register;
import com.entities.Person;
import com.entities.Course;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import static com.utils.RegisterUtils.hasScheduleConflict;

@Service
public class RegisterPersistenceService {
    private final RegisterRepository repository;
    private final PersonRepository people;
    private final CourseRepository courses;

    public RegisterPersistenceService(RegisterRepository repository, PersonRepository people, CourseRepository courses) {
        this.repository = repository;
        this.people = people;
        this.courses = courses;
    }

    /** Recebe pessoa/inscrição novas e normalizadas, com referência ao ID de um curso existente. */
    @Transactional
    public RegisterImportResult persist(SheetImportResult collection) {
        int inserted = 0;
        int unchanged = 0;
        var conflicts = new ArrayList<RegisterImportResult.Conflict>();
        var incomingPeople = new TreeMap<String, Person>();
        var savedCourses = new TreeMap<Long, Course>();
        for (Register incoming : collection.registers()) {
            Person incomingPerson = incoming.getPerson();
            if (incomingPerson == null || incomingPerson.getCpf() == null
                    || !incomingPerson.getCpf().matches("[0-9]{11}")) {
                throw new IllegalArgumentException("A persistência requer CPF normalizado com 11 dígitos.");
            }
            if (incoming.getId() != null || incomingPerson.getId() != null || incomingPerson.getAddress() == null
                    || incomingPerson.getAddress().getId() != null) {
                throw new IllegalArgumentException("A importação requer inscrição, pessoa e endereço novos, sem IDs.");
            }
            if (incoming.getCourseOfInterest() == null || incoming.getCourseOfInterest().getId() == null
                    || incoming.getCourseOfInterest().getId() <= 0) {
                throw new IllegalArgumentException("A importação requer o ID positivo de um curso cadastrado.");
            }
            incomingPeople.putIfAbsent(incomingPerson.getCpf(), incomingPerson);
            savedCourses.put(incoming.getCourseOfInterest().getId(), null);
        }
        // Bloquear todo o lote na mesma ordem, mesmo quando as linhas chegam invertidas.
        for (Long courseId : savedCourses.keySet()) {
            savedCourses.put(courseId, courses.findByIdForRegistration(courseId)
                    .orElseThrow(() -> new EntityNotFoundException("Curso informado na importação não encontrado.")));
        }
        var savedPeople = new TreeMap<String, Person>();
        for (var entry : incomingPeople.entrySet()) {
            savedPeople.put(entry.getKey(), people.findByCpfForUpdate(entry.getKey())
                    .orElseGet(() -> people.save(entry.getValue())));
        }
        // Preservar a ordem original e a precedência da primeira ocorrência no relatório.
        for (Register incoming : collection.registers()) {
            Person incomingPerson = incoming.getPerson();
            Course savedCourse = savedCourses.get(incoming.getCourseOfInterest().getId());
            Person savedPerson = savedPeople.get(incomingPerson.getCpf());
            var fields = differences(savedPerson, incomingPerson);
            var existing = repository.findByPerson_CpfAndCourseOfInterest_Id(
                    savedPerson.getCpf(), savedCourse.getId());
            if (existing.isEmpty()) {
                incoming.setPerson(savedPerson);
                incoming.setCourseOfInterest(savedCourse);
                if (hasScheduleConflict(repository, incoming)) {
                    throw new IllegalArgumentException(
                            "Não é possível se inscrever em cursos com horários conflitantes."
                    );
                }
                repository.save(incoming);
                inserted++;
                if (!fields.isEmpty()) {
                    conflicts.add(new RegisterImportResult.Conflict(incoming.getId(), fields));
                }
            } else {
                compare(fields, "registerDate", existing.get().getRegisterDate(), incoming.getRegisterDate());
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

    private List<String> differences(Person saved, Person incoming) {
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
        return fields;
    }

    private void compare(List<String> fields, String field, Object saved, Object incoming) {
        if (!Objects.equals(saved, incoming)) {
            fields.add(field);
        }
    }
}
