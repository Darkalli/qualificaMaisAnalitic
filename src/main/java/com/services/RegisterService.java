package com.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Course;
import com.entities.Register;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public void addRegister (AddRegisterDto registerDto){
        Course course = courseRepository.findById(registerDto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o Id: " + registerDto.courseOfInterestId()));
        registerRepository.save(new Register(personRepository.findByCpf(registerDto.personCpf())
                .orElseThrow(() -> new EntityNotFoundException("Aluno(a) não encontrado(a) com o cpf: " + registerDto.personCpf())),
                course, registerDto.registerDate()));
    }

    @Transactional
    public void deleteRegister (SearchRegisterDto delete){
        registerRepository.delete(registerRepository.findByPerson_CpfAndCourseOfInterest_Id(delete.personCpf(), delete.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa e o curso informados.")));
    }

    public List<Register> getAllRegisterByCpf(String cpf){
        return registerRepository.findByPerson_Cpf(cpf);
    }

    public Register getByPersonCpfAndCourseOfInterest (SearchRegisterDto registerDto){
        return registerRepository.findByPerson_CpfAndCourseOfInterest_Id(registerDto.personCpf(), registerDto.courseOfInterestId())
                .orElseThrow(() -> new EntityNotFoundException("Inscrição não encontrada para a pessoa e o curso informados."));
    }
}
