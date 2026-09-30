package com.controlers;

import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.presenceDtos.PresenceByDayAndCourseDto;
import com.dtos.presenceDtos.PresenceUpdateDto;
import com.entities.Presence;
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
    public ResponseEntity<Presence> createPresence(@RequestBody AddPresenceDto presenceDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(presenceService.addPresence(presenceDto));
    }

    @PatchMapping
    public ResponseEntity<Presence> updatePresence(@RequestBody PresenceUpdateDto updateDto){
        return ResponseEntity.ok().body(presenceService.updatePresence(updateDto));
    }

    @GetMapping("/presence/{personId}")
    public ResponseEntity<List<Presence>> getPresenceByPerson(@PathVariable Long personId){
        return ResponseEntity.ok().body(presenceService.getPresenceByPerson(personId));
    }

    @GetMapping
    public ResponseEntity<List<Presence>> getPresenceByDateAndCourse(@RequestBody PresenceByDayAndCourseDto byDayAndCourseDto){
        return ResponseEntity.ok().body(presenceService.getPresenceByDateAndCourse(byDayAndCourseDto));
    }

}
