package com.controlers;


import com.dtos.CourseDtos.UpdateCourseDto;
import com.dtos.personDtos.AddPersonDto;
import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Person;
import com.services.PersonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/person")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @PostMapping
    public ResponseEntity<Person> createPerson(@RequestBody AddPersonDto addPersonDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(personService.addPerson(addPersonDto));
    }

    @PatchMapping
    public ResponseEntity<Person> updatePerson(@RequestBody UpdatePersonDto update){
        return ResponseEntity.ok().body(personService.updatePerson(update));
    }

    @DeleteMapping("/person/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id){
        personService.deletePerson(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Person>> getAllPersons(){
        return ResponseEntity.ok().body(personService.getAllPerson());
    }

    @GetMapping("/person/{cpf}")
    public ResponseEntity<Person> getById(@PathVariable String cpf){
        return ResponseEntity.ok(personService.getByCpf(cpf));
    }
}
