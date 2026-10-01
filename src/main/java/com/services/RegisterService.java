package com.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import com.entities.Register;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.utils.CpfUtils.*;
import static com.utils.RegisterUtils.*;

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
    public Register addRegister (AddRegisterDto dto){
        String cpf = formatCpf(dto.personCpf());
        cpf = cleanCpf(cpf);
        Course course = courseRepository.findById(dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o Id: " + dto.courseOfInterestId()));
        Person person = personRepository.findByCpf(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada com o cpf: " + dto.personCpf()));
        Register newRegister = new Register(person, course, dto.registerDate());
        if (hasScheduleConflict(registerRepository, newRegister)) {
            throw new IllegalArgumentException("Não é possivel se inscrever em cursos com mesmos horarios");
        }else {
            return registerRepository.save(newRegister);
        }
    }

    @Transactional
    public void deleteRegister (SearchRegisterDto dto){
        String cpf = formatCpf(dto.personCpf());
        cpf = cleanCpf(cpf);
        registerRepository.delete(registerRepository.findByPerson_CpfAndCourseOfInterest_Id(cpf, dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa e o curso informados.")));
    }

    public List<Register> getAllRegisterByCpf(String cpfIn){
        String cpf = formatCpf(cpfIn);
        cpf = cleanCpf(cpf);
        return registerRepository.findByPerson_Cpf(cpf);
    }

    public Register getByPersonCpfAndCourseOfInterest (SearchRegisterDto dto){
        String cpf = formatCpf(dto.personCpf());
        cpf = cleanCpf(cpf);
        return registerRepository.findByPerson_CpfAndCourseOfInterest_Id(cpf, dto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa e o curso informados."));
    }
}