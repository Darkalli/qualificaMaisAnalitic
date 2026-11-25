package com.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Course;
import com.entities.Person;
import com.entities.Register;

import com.enums.StatusRegister;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import jakarta.persistence.EntityNotFoundException;
import com.exceptions.ConflictException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;

import static com.utils.CpfUtils.*;
import static com.utils.RegisterUtils.*;
import static com.utils.ValidationUtils.*;

@Service
public class RegisterService {

    private final RegisterRepository registerRepository;
    private final PersonRepository personRepository;
    private final CourseRepository courseRepository;

    public RegisterService(RegisterRepository registerRepository, PersonRepository personRepository, CourseRepository courseRepository) {
        this.registerRepository = registerRepository;
        this.personRepository = personRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Register addRegister(AddRegisterDto dto) {
        validateKey(dto.personCpf(), dto.courseOfInterestId());
        String cpf = formatCpf(dto.personCpf());
        cpf = cleanCpf(cpf);
        Course course = courseRepository.findByIdForUpdate(dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o Id: " + dto.courseOfInterestId()));
        Person person = personRepository.findByCpfForUpdate(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada"));
        Register newRegister = new Register(person, course, LocalDateTime.now(), StatusRegister.ACTIVE);
        if (hasScheduleConflict(registerRepository, newRegister)) {
            throw new ConflictException("A pessoa já está inscrita em um curso com horário conflitante.");
        } else {
            if (isCourseFull(course)) {
                newRegister.setStatus(StatusRegister.WAITLISTED);
            }
            return registerRepository.save(newRegister);
        }
    }


    @Transactional
    public String reactiveRegister(SearchRegisterDto dto) {
        Register register = findRegistrationAfterLocks(dto);
        Course course = courseRepository.findByid(dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o Id: " + dto.courseOfInterestId()));
        if (register.getStatus() == StatusRegister.ACTIVE) {
            return "Estado do registro já está como ativo";
        }
        if (hasScheduleConflict(registerRepository, register)) {
            throw new ConflictException("Não é possível reativar: existe inscrição em outro curso com horário conflitante.");
        }
        if (isCourseFull(course)) {
            register.setStatus(StatusRegister.WAITLISTED);
        } else {
            register.setStatus(StatusRegister.ACTIVE);
        }
        registerRepository.save(register);
        return "Estado do registro atualizado com sucesso";
    }

    @Transactional
    public void deleteRegister(SearchRegisterDto dto) {
        Register register = findRegistrationAfterLocks(dto);
        Course course = courseRepository.findByid(dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o Id: " + dto.courseOfInterestId()));
        if (register.getStatus() == StatusRegister.CANCELED) {
            return;
        }
        boolean occupiedPlace = register.getStatus() == StatusRegister.ACTIVE;
        register.setStatus(StatusRegister.CANCELED);
        registerRepository.save(register);
        if (occupiedPlace && !isCourseFull(course)) {
            registerRepository.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                    course.getId(), StatusRegister.WAITLISTED)
                    .ifPresent(waitlisted -> waitlisted.setStatus(StatusRegister.ACTIVE));
        }
    }

    public List<Register> getAllRegisters() {
        return registerRepository.findAll();
    }

    public List<Register> getAllRegisterByCpf(String cpfIn) {
        required(cpfIn, "cpf");
        String cpf = formatCpf(cpfIn);
        cpf = cleanCpf(cpf);
        return registerRepository.findByPerson_Cpf(cpf);
    }

    public Register getByPersonCpfAndCourseOfInterest(String cpf, Long courseId) {
        validateKey(cpf, courseId);
        String newCpf = formatCpf(cpf);
        newCpf = cleanCpf(newCpf);
        return registerRepository.findByPerson_CpfAndCourseOfInterest_Id(newCpf, courseId)
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa e o curso informados."));
    }

    private Register findRegistrationAfterLocks(SearchRegisterDto dto) {
        required(dto, "inscrição");
        validateKey(dto.personCpf(), dto.courseOfInterestId());
        String cpf = cleanCpf(formatCpf(dto.personCpf()));
        Course course = courseRepository.findByIdForUpdate (dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para o curso informado."));
        Person person = personRepository.findByCpfForUpdate(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa informada."));
        return registerRepository.findByPerson_CpfAndCourseOfInterest_Id(person.getCpf(), course.getId())
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa e o curso informados."));
    }

    private void validateKey(String cpf, Long courseId) {
        required(cpf, "personCpf");
        positiveId(courseId, "courseOfInterestId");
    }

    private boolean isCourseFull(Course course) {
        int counted = registerRepository.countAllActiveByCourseId(course.getId());
        int max = course.getMaximumStudentsCount();
        return max <= counted;
    }
}
