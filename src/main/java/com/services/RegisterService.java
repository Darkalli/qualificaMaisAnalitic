package com.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Register;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegisterService {

    private final RegisterRepository registerRepository;
    private final PersonRepository personRepository;

    public RegisterService(RegisterRepository registerRepository, PersonRepository personRepository) {
        this.registerRepository = registerRepository;
        this.personRepository = personRepository;
    }

    public void addRegister (AddRegisterDto registerDto){
        registerRepository.save(new Register(personRepository.findByCpf(registerDto.personCpf())
                .orElseThrow(() -> new EntityNotFoundException("Aluno(a) não encontrado(a) com o cpf: " + registerDto.personCpf())),
                registerDto.courseOfInterest(), registerDto.registerDate()));
    }

    public void deleteRegister (SearchRegisterDto delete){
        registerRepository.delete(registerRepository.findByPerson_CpfAndCourseOfInterest(delete.personCpf(), delete.courseOfInterest())
                .orElseThrow(() -> new EntityNotFoundException("Registro não encontrado com o cpf: " + delete.personCpf() + "Ou o curso: " + delete.courseOfInterest())));
    }

    public List<Register> getAllRegisterByCpf(String cpf){
        return registerRepository.findByPerson_Cpf(cpf);
    }

    public Register getByPersonCpfAndCourseOfInterest (SearchRegisterDto registerDto){
        return registerRepository.findByPerson_CpfAndCourseOfInterest(registerDto.personCpf(), registerDto.courseOfInterest())
                .orElseThrow(() -> new EntityNotFoundException("Registro não encontrado com o cpf: " + registerDto.personCpf() + "Ou o curso: " + registerDto.courseOfInterest()));
    }
}
