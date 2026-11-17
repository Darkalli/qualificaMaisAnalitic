# PDF reports

The API generates eight PDF documents using Apache PDFBox 3. Code and routes are in English; document text is in Portuguese. Each document has wrapped text, automatic pagination and page numbers. Table headings repeat on subsequent pages.

## Authentication and downloads

All routes require a bearer token for an `ADMIN` or `AGENT`. These are the staff roles currently available in the API. The participant record contains personal and sensitive information and uses the same explicit staff restriction. All downloads return `application/pdf`, `Content-Disposition: attachment` and `Cache-Control: no-store`.

Base path: `/api/reports`.

| Document | GET route | Optional query parameters |
| --- | --- | --- |
| Attendance report | `/api/reports/attendance?courseId=1` | `start`, `finish` |
| Registration proof | `/api/reports/registrations/1/proof` | None |
| Participant record | `/api/reports/participants/1/record` | None |
| Enrollment list | `/api/reports/courses/1/enrollments` | `includeContact=false` |
| Course schedule | `/api/reports/courses/1/schedule` | `start`, `finish` |
| Participation history | `/api/reports/participants/1/history` | `start`, `finish` |
| Participation declaration | `/api/reports/registrations/1/declaration` | `start`, `finish` |
| Completion certificate | `/api/reports/registrations/1/certificate` | None |

Path IDs refer to existing course, person or registration IDs, as indicated by the route. Dates use `yyyy-MM-dd`; both boundaries are inclusive and can be supplied independently. No boundary means no limit on that side. Attendance, declaration and history filters select class dates. History includes registrations for courses whose periods overlap the filter, including courses with zero attendance; its course status and registration status are displayed explicitly.

Example in PowerShell:

```powershell
$headers = @{ Authorization = "Bearer YOUR_TOKEN" }
Invoke-WebRequest -Uri 'http://localhost:8080/api/reports/attendance?courseId=1&start=2026-11-01&finish=2026-11-30' -Headers $headers -OutFile attendance-report.pdf
```

## Course settings

The current course model has no signatories or completion policy. Configure these per course in your local `application.properties` (or equivalent Spring configuration), using the real database course ID:

```properties
app.reports.organization=Qualifica+
app.reports.zone=America/Sao_Paulo
app.reports.courses[1].signatories[0]=Full name - Coordinator
app.reports.courses[1].signatories[1]=Full name - Instructor
# Example only: replace these with the department's approved criteria.
app.reports.courses[1].minimum-attendance-percentage=75
app.reports.courses[1].minimum-attended-hours=0
```

There is no default minimum percentage. A missing policy blocks certificate generation with HTTP 409. Signatories are optional for declarations and certificates: configured names appear below signature lines; when no nonblank names are configured, the responsible section is omitted. Registration proof and schedules do not require course settings and do not include a location. Settings are server configuration, not database fields or request parameters.

## Attendance and hours

- Only `ACTIVE` classes whose scheduled finish time has passed are considered in attendance calculations. `CANCELED`, `POSTPONED`, future and ongoing classes are excluded. This model has no separate confirmation that a scheduled class actually took place.
- Attendance reports, history and declarations exclude classes preceding the registration date. Registration has a date only, so classes on that date are included regardless of the registration time.
- `PRESENT`, `ABSENT`, `JUSTIFIED` and missing attendance are displayed separately. A missing record is **pending**, never an assumed absence. Justified absences do not count as presence.
- Frequency = confirmed presences / all eligible classes × 100. Pending records remain in the denominator. Empty periods display 0%.
- Hours are the sum of each eligible class's finish minus start, with attended hours counting only `PRESENT`. Durations must be positive. Outputs show hours and minutes without losing fractional hours.
- A declaration requires at least one confirmed presence, uses the first and last attended dates and reports only verified attended hours. Historical participation may be declared even if the registration is currently canceled.
- Enrollment lists omit contact details by default; `includeContact=true` adds the personal phone and email. CPF is included only in the restricted participant record.
- Canceled registrations remain visible for historical consultation. Because the model has no cancellation timestamp, reports cannot reconstruct the active enrollment interval or stop counting at the exact cancellation date. Registration proof and certificates reject canceled registrations.

## Certificate eligibility

Certification requires an active registration, a `CLOSED` course whose finish date has arrived, at least one active class, all active classes already ended, at least one confirmed presence and no pending attendance. It assesses the **whole course**, including classes before a late registration, so a late enrollment cannot turn a small part of the course into full completion.

The configured minimum frequency (0-100) and minimum attended hours (nonnegative, default 0) are checked against exact values before display rounding. Justified absences do not satisfy either requirement. The certificate states the period from the first to the last active conducted class, total class duration, attended hours, frequency, applied thresholds and responsible names.

These are the completion criteria currently supported by the data model. Grades, practical assessments, manual approval, digital signatures, certificate validation codes and a permanent issuance/revocation register are not implemented. Responsible names and signature lines do not constitute a digital signature. If the department requires additional criteria, add them to the eligibility checks before enabling issuance.

## Errors and validation

- 400: invalid/nonpositive IDs, malformed dates or an inverted date range.
- 401: missing/invalid authentication.
- 403: authenticated user without a staff role.
- 404: course, participant or registration does not exist.
- 409: missing required course settings, unsupported class duration or unmet issuance criteria.

Errors always return `application/json` with the original HTTP status, including when the client sends `Accept: application/pdf` (as Swagger does). Successful downloads return `application/pdf`. Clients should handle both response types.

The renderer uses PDFBox's built-in Helvetica fonts. Portuguese accents are supported. Control characters are normalized to spaces; characters outside the font's repertoire are replaced with `?`. For documents requiring other writing systems, embed an appropriately licensed Unicode font.

## Verification

`PdfControllerTests` exercises the real MVC routes, security, repositories and PDF generation with an H2 database and a fixed clock. It covers all eight downloads, permissions, date filters, cancellation, pending/justified attendance, optional contacts, calculated hours, certificate thresholds and multipage tables. Generated PDFs and rendered previews used for visual inspection are written to the ignored `target/pdf-qa` directory.

Run the report checks with `mvn -Dtest=PdfControllerTests test`, or run `mvn test` for the complete suite.
