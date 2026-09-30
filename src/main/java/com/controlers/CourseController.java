package com.controlers;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.entities.Person;
import com.services.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(AddCourseDto courseDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.addCourse(courseDto));
    }

    @PatchMapping
    public ResponseEntity<Course> updateCourse(UpdateCourseDto updateCourseDto){
        return ResponseEntity.ok().body(courseService.updateCourse(updateCourseDto));
    }

    @DeleteMapping("/course/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id){
        courseService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Course>> getAllCourses(){
        return ResponseEntity.ok().body(courseService.getAllCourses());
    }

    @GetMapping("/course/{name}")
    public ResponseEntity<Course> getCourseByName(String name){
        return ResponseEntity.ok(courseService.getCourseByName(name));
    }

}
