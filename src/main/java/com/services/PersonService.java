package com.services;

import com.dtos.personDtos.AddPersonDto;
import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Address;
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
import static com.utils.ValidationUtils.*;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper mapper;

    public PersonService(PersonRepository personRepository, PersonMapper mapper) {
        this.personRepository = personRepository;
        this.mapper = mapper;
    }

    public Person addPerson (AddPersonDto addPerson){
        required(addPerson.fullName(), "fullName");
        required(addPerson.cpf(), "cpf");
        email(addPerson.email());
        required(addPerson.personalPhone(), "personalPhone");
        required(addPerson.personalPhoneHasWhatsapp(), "personalPhoneHasWhatsapp");
        required(addPerson.gender(), "gender");
        required(addPerson.education(), "education");
        required(addPerson.workState(), "workState");
        address(addPerson.address());
        String cpf = formatCpf(addPerson.cpf());
        cpf = cleanCpf(cpf);
        String cellphone = cleanPhone(addPerson.personalPhone());
        String familyPhone = cleanPhone(addPerson.familyPhone());
        Set<Disabilities> newDisabilities = Disabilities.processAndValidateDisabilities(
                addPerson.disabilities() == null ? null : new ArrayList<>(addPerson.disabilities()));
        Address address = new Address(addPerson.address().getNumber(),
                addPerson.address().getStreet(), addPerson.address().getNeighborhood());

        return personRepository.save(new Person(addPerson.fullName(), addPerson.socialName(), cpf ,
                addPerson.email(), cellphone, addPerson.personalPhoneHasWhatsapp(),
                familyPhone, address, addPerson.gender(), addPerson.education(),
                addPerson.workState(),newDisabilities));
    }

    @Transactional
    public Person updatePerson (UpdatePersonDto updatePerson){
        required(updatePerson.Cpf(), "Cpf");
        String cpf = formatCpf(updatePerson.Cpf());
        cpf = cleanCpf(cpf);
        Person person = personRepository.findByCpf(cpf)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada."));
        if (updatePerson.email() != null) {
            email(updatePerson.email());
        }
        if (updatePerson.personalPhone() != null) {
            required(updatePerson.personalPhone(), "personalPhone");
        }
        Address changedAddress = null;
        if (updatePerson.address() != null) {
            Address incoming = updatePerson.address();
            Address current = person.getAddress();
            if (incoming.getId() != null && !incoming.getId().equals(current.getId())) {
                throw new IllegalArgumentException("O campo 'address.id' não pode alterar o endereço vinculado à pessoa.");
            }
            changedAddress = new Address(incoming.getNumber() != null ? incoming.getNumber() : current.getNumber(),
                    incoming.getStreet() != null ? incoming.getStreet() : current.getStreet(),
                    incoming.getNeighborhood() != null ? incoming.getNeighborhood() : current.getNeighborhood());
            address(changedAddress);
        }
        String cellphone = updatePerson.personalPhone() == null
                ? person.getPersonalPhone() : cleanPhone(updatePerson.personalPhone());
        String familyPhone = updatePerson.familyPhone() == null
                ? person.getFamilyPhone() : cleanPhone(updatePerson.familyPhone());
        Set<Disabilities> newDisabilities = updatePerson.disabilities() == null ? null
                : Disabilities.processAndValidateDisabilities(new ArrayList<>(updatePerson.disabilities()));
        mapper.updatePersonfromDto(updatePerson, person);
        if (changedAddress != null) {
            person.getAddress().setNumber(changedAddress.getNumber());
            person.getAddress().setStreet(changedAddress.getStreet());
            person.getAddress().setNeighborhood(changedAddress.getNeighborhood());
        }
        if (newDisabilities != null) {
            person.setDisabilities(newDisabilities);
        }
        person.setCpf(cpf);
        person.setPersonalPhone(cellphone);
        person.setFamilyPhone(familyPhone);
        return personRepository.save(person);
    }

   public void deletePerson (Long id){
       positiveId(id, "id");
       Person person = personRepository.findById(id)
               .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada"));
       personRepository.delete(person);
   }

   public List<Person> getAllPerson (){
        return personRepository.getAll();
   }

   public Person getByCpf (String cpf){
       required(cpf, "cpf");
       String formatedCpf = formatCpf(cpf);
       formatedCpf = cleanCpf(formatedCpf);
        return personRepository.findByCpf(formatedCpf)
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada."));
   }
}
