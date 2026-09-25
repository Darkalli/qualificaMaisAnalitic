package com.sheets.repositories;

import com.sheets.entities.Register;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {
    Optional<Register> findByCpf(String cpf);
}
