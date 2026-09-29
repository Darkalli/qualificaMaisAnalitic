package com.services;

import com.dtos.personDtos.AddPersonDto;
import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Person;
import com.mappers.PersonMapper;
import com.repositories.PersonRepository;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper mapper;

    public PersonService(PersonRepository personRepository, PersonMapper mapper) {
        this.personRepository = personRepository;
        this.mapper = mapper;
    }

    public void addPerson (AddPersonDto addPerson){
        personRepository.save(new Person(addPerson.fullName(), addPerson.socialName(), addPerson.cpf(),
                addPerson.email(), addPerson.personalPhone(), addPerson.personalPhoneHasWhatsapp(),
                addPerson.familyPhone(), addPerson.address(), addPerson.gender(), addPerson.education(),
                addPerson.workState(),addPerson.disabilities()));
    }

    public void updatePerson (UpdatePersonDto updatePerson) throws Exception {
        Person person = personRepository.findByCpf(updatePerson.Cpf())
                .orElseThrow(() -> new Exception("Usuário não encontrado"));
        mapper.updatePersonfromDto(updatePerson, person);
        personRepository.save(person);
    }

   public void deletePerson (Long id){
       Person person = personRepository.getById(id);
       personRepository.delete(person);
   }

   public List<Person> getAllPerson (String filter){
        return personRepository.findAll(Sort.by(Sort.Direction.ASC, filter));
   }

   public Optional<Person> getByCpf (String cpf){
        return personRepository.findByCpf(cpf);
   }
}
