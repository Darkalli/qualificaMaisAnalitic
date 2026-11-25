package com.repositories;

import com.entities.Course;
import com.entities.Person;
import com.entities.Register;
import com.enums.StatusRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {

    List<Register> findByCourseOfInterest_IdOrderByPerson_FullNameAsc(Long courseId);

    List<Register> findByPerson_IdOrderByRegisterDateDesc(Long personId);

    Optional<Register> findByPerson_CpfAndCourseOfInterest_Id(String cpf, Long courseId);

    List<Register> findByPerson_Cpf(String cpf);

    @Query("select distinct r.person.cpf from Register r where r.courseOfInterest.id = :courseId and r.status = com.enums.StatusRegister.ACTIVE order by r.person.cpf")
    List<String> findPersonCpfsByCourseId(Long courseId);

    void deleteByPerson_Id(Long personId);

    @Query("SELECT COUNT(*) FROM Register r WHERE r.courseOfInterest.id = :courseId and r.status = com.enums.StatusRegister.ACTIVE")
    int countAllActiveByCourseId(@Param("courseId")Long courseId);

    Optional<Register> findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(Long courseId, StatusRegister status);
}
