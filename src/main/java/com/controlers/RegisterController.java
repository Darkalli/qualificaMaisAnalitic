package com.controlers;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;

import com.entities.Register;

import com.services.RegisterService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/register")
public class RegisterController {

    private final RegisterService registerService;

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @PostMapping
    public ResponseEntity<Register> createRegister(@RequestBody AddRegisterDto registerDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(registerService.addRegister(registerDto));
    }

    @PatchMapping("/active")
    public ResponseEntity<String> updateRegisterStatusToActive(@RequestBody SearchRegisterDto dto) {
        String message = registerService.reactiveRegister(dto);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteRegister(@RequestBody SearchRegisterDto delete){
        registerService.deleteRegister(delete);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/register/{cpf}")
    public ResponseEntity<List<Register>> getAllRegisterByCpf(@PathVariable String cpf){
        return ResponseEntity.ok().body(registerService.getAllRegisterByCpf(cpf));
    }

    @GetMapping
    public ResponseEntity<Register> getByPersonCpfAndCourseOfInterest(@RequestBody SearchRegisterDto search){
        return ResponseEntity.ok(registerService.getByPersonCpfAndCourseOfInterest(search));
    }
}
