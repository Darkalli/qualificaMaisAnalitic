package com.services;

import com.dtos.personDtos.AddPersonDto;
import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Person;
import com.enums.Disabilities;
import com.mappers.PersonMapper;
import com.repositories.PersonRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import static com.utils.CpfUtils.*;
import static com.utils.CellphoneUtils.*;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper mapper;

    public PersonService(PersonRepository personRepository, PersonMapper mapper) {
        this.personRepository = personRepository;
        this.mapper = mapper;
    }

    public Person addPerson (AddPersonDto addPerson){
        String cpf = formatCpf(addPerson.cpf());
        cpf = cleanCpf(cpf);
        String cellphone = cleanPhone(addPerson.personalPhone());
        String familyPhone = cleanPhone(addPerson.familyPhone());
        Set<Disabilities> newDisabilities = Disabilities.processAndValidateDisabilities(
                addPerson.disabilities() == null ? null : new ArrayList<>(addPerson.disabilities()));
        return personRepository.save(new Person(addPerson.fullName(), addPerson.socialName(), cpf ,
                addPerson.email(), cellphone, addPerson.personalPhoneHasWhatsapp(),
                familyPhone, addPerson.address(), addPerson.gender(), addPerson.education(),
                addPerson.workState(),newDisabilities));
    }

    @Transactional
    public Person updatePerson (UpdatePersonDto updatePerson){
        String cpf = formatCpf(updatePerson.Cpf());
        cpf = cleanCpf(cpf);
        Person person = personRepository.findByCpf(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        String cellphone = updatePerson.personalPhone() == null
                ? person.getPersonalPhone() : cleanPhone(updatePerson.personalPhone());
        String familyPhone = updatePerson.familyPhone() == null
                ? person.getFamilyPhone() : cleanPhone(updatePerson.familyPhone());
        Set<Disabilities> newDisabilities = updatePerson.disabilities() == null ? null
                : Disabilities.processAndValidateDisabilities(new ArrayList<>(updatePerson.disabilities()));
        mapper.updatePersonfromDto(updatePerson, person);
        if (newDisabilities != null) {
            person.setDisabilities(newDisabilities);
        }
        person.setCpf(cpf);
        person.setPersonalPhone(cellphone);
        person.setFamilyPhone(familyPhone);
        return personRepository.save(person);
    }

   public void deletePerson (Long id){
       Person person = personRepository.getById(id);
       personRepository.delete(person);
   }

   public List<Person> getAllPerson (){
        return personRepository.getAll();
   }

   public Person getByCpf (String cpf){
       String formatedCpf = formatCpf(cpf);
       formatedCpf = cleanCpf(formatedCpf);
        return personRepository.findByCpf(formatedCpf)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
   }
}
