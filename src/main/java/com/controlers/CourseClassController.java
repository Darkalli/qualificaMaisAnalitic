package com.controlers;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.AddCourseClassInBatchDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.CourseClass;
import com.services.CourseClassService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courseClass")
public class CourseClassController {

    private final CourseClassService courseClassService;

    public CourseClassController(CourseClassService courseClassService) {
        this.courseClassService = courseClassService;
    }

    @PostMapping
    public ResponseEntity<CourseClass> createCourseClass(@RequestBody AddCourseClassDto courseClassDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(courseClassService.addCourseClass(courseClassDto));
    }

    @PostMapping("/courseClass/batch")
    public ResponseEntity<List<CourseClass>> createCourseClassInBatch(@RequestBody AddCourseClassInBatchDto dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(courseClassService.addCourseClassInBatch(dto));
    }

    @PatchMapping
    public ResponseEntity<CourseClass> updateCourseClass(@RequestBody UpdateCourseClassDto updateCourseClassDto){
        return ResponseEntity.ok().body(courseClassService.updateCourseClass(updateCourseClassDto));
    }

    @DeleteMapping("/courseClass/{id}")
    public ResponseEntity<Void> deleteCourseClass(@PathVariable Long id){
        courseClassService.deleteCourseClass(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/courseClass/{courseId}")
    public ResponseEntity<List<CourseClass>> getAllByCourseId(@PathVariable Long courseId){
        return ResponseEntity.ok().body(courseClassService.allClassesByCourseId(courseId));
    }

}
