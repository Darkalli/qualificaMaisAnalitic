package com.controlers;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.presenceDtos.PresenceByDayAndCourseDto;
import com.dtos.presenceDtos.PresenceByPersonDto;
import com.dtos.presenceDtos.PresenceUpdateDto;
import com.entities.Course;
import com.entities.Presence;
import com.services.CourseService;
import com.services.PresenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping
    public ResponseEntity<Presence> createPresence(AddPresenceDto presenceDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(presenceService.addPresence(presenceDto));
    }

    @PatchMapping
    public ResponseEntity<Presence> updateCourse(PresenceUpdateDto updateDto){
        return ResponseEntity.ok().body(presenceService.updatePresence(updateDto));
    }

    @GetMapping
    public ResponseEntity<List<Presence>> getPresenceByPerson(PresenceByPersonDto byPersonDto){
        return ResponseEntity.ok().body(presenceService.getPresenceByPerson(byPersonDto));
    }

    @GetMapping
    public ResponseEntity<List<Presence>> getPresenceByDateAndCourse(PresenceByDayAndCourseDto byDayAndCourseDto){
        return ResponseEntity.ok().body(presenceService.getPresenceByDateAndCourse(byDayAndCourseDto));
    }

}
