package com.controlers;

import com.services.PdfService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping(value = "/api/reports", produces = MediaType.APPLICATION_PDF_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
public class PdfController {
    private final PdfService pdfService;

    public PdfController(PdfService pdfService) { this.pdfService = pdfService; }

    @GetMapping("/attendance")
    public ResponseEntity<byte[]> attendance(@RequestParam Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate finish) throws IOException {
        return download(pdfService.attendanceReport(courseId, start, finish), "attendance-report.pdf");
    }

    @GetMapping("/registrations/{registerId}/proof")
    public ResponseEntity<byte[]> registrationProof(@PathVariable Long registerId) throws IOException {
        return download(pdfService.registrationProof(registerId), "registration-proof.pdf");
    }

    @GetMapping("/participants/{personId}/record")
    public ResponseEntity<byte[]> participantRecord(@PathVariable Long personId) throws IOException {
        return download(pdfService.participantRecord(personId), "participant-record.pdf");
    }

    @GetMapping("/courses/{courseId}/enrollments")
    public ResponseEntity<byte[]> enrollments(@PathVariable Long courseId,
            @RequestParam(defaultValue = "false") boolean includeContact) throws IOException {
        return download(pdfService.enrollmentList(courseId, includeContact), "enrollment-list.pdf");
    }

    @GetMapping("/courses/{courseId}/schedule")
    public ResponseEntity<byte[]> schedule(@PathVariable Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate finish) throws IOException {
        return download(pdfService.courseSchedule(courseId, start, finish), "course-schedule.pdf");
    }

    @GetMapping("/participants/{personId}/history")
    public ResponseEntity<byte[]> history(@PathVariable Long personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate finish) throws IOException {
        return download(pdfService.participationHistory(personId, start, finish), "participation-history.pdf");
    }

    @GetMapping("/registrations/{registerId}/declaration")
    public ResponseEntity<byte[]> declaration(@PathVariable Long registerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate finish) throws IOException {
        return download(pdfService.participationDeclaration(registerId, start, finish), "participation-declaration.pdf");
    }

    @GetMapping("/registrations/{registerId}/certificate")
    public ResponseEntity<byte[]> certificate(@PathVariable Long registerId) throws IOException {
        return download(pdfService.completionCertificate(registerId), "completion-certificate.pdf");
    }

    private ResponseEntity<byte[]> download(byte[] pdf, String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
