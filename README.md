# Medical Clinic Backend

This module contains the Spring Boot backend for the medical clinic workshop. It exposes a simple REST API for the frontend, while internally coordinating multiple in-memory subsystems through a facade.

## Stack

- Java 17
- Spring Boot 3
- `spring-boot-starter-web`
- `spring-boot-starter-test`

## Current Scope

The backend is intentionally implemented without a database. All data lives in memory while the application is running.

That means:

- Restarting the application resets patients, appointments, prescriptions, and laboratory orders
- There is no persistence layer, repository layer, or external service integration
- The API is designed for the workshop demo and for frontend integration

## Package Structure

The current implementation lives under `com.example.api.clinic`.

- `ClinicController`: exposes the REST endpoints under `/api/clinica`
- `ClinicFacade`: orchestrates cross-service flows and hides subsystem complexity
- `ClinicDomain`: shared request/response models, enums, and validation helpers
- `PatientService`: patient registration, unique document validation, patient profile access
- `AgendaService`: predefined doctors, specialties, availability, and appointment scheduling
- `MedicalRecordService`: stores consultation records associated with a patient
- `PrescriptionService`: creates prescriptions and validates medications against allergies
- `LaboratoryService`: simulates laboratory orders and exam results

## Architecture

The backend follows a small facade-oriented design:

1. The frontend talks only to REST endpoints.
2. `ClinicController` delegates business flows to `ClinicFacade`.
3. `ClinicFacade` coordinates the required subsystems for each use case.
4. Subsystems keep their own in-memory state.

This keeps the frontend contract simple while still reflecting the workshop requirement that real clinic operations involve multiple internal services.

## Facade Responsibilities

`ClinicFacade` is the main orchestration layer and implements the required workshop flows:

- `registerPatient(...)`
  - Registers the patient in `PatientService`
  - Initializes a clinical record in `MedicalRecordService`

- `agendarCita(...)`
  - Validates that the patient exists using `PatientService`
  - Schedules the appointment using `AgendaService`

- `verHistoriaCompleta(...)`
  - Loads patient data from `PatientService`
  - Consolidates consultations from `MedicalRecordService`
  - Consolidates past appointments from `AgendaService`
  - Consolidates prescriptions from `PrescriptionService`
  - Consolidates laboratory orders from `LaboratoryService`

- `generarPrescripcion(...)`
  - Validates the patient and allergies with `PatientService`
  - Creates the prescription in `PrescriptionService`
  - Registers a consultation event in `MedicalRecordService`

- `solicitarExamenes(...)`
  - Validates the patient with `PatientService`
  - Creates the laboratory order in `LaboratoryService`
  - Registers a consultation event in `MedicalRecordService`

## In-Memory Business Rules

### Patient

- A patient document must be unique
- Allergies are stored as part of the patient profile

### Agenda

- At least 3 specialties are available
- 5 doctors are predefined in memory
- Each doctor starts with generated future slots
- Scheduling consumes one available slot
- Each successful appointment includes a reminder message

### Prescription

- At least one medication is required
- A prescription is rejected if any medication matches a patient allergy

### Laboratory

- Supported exams:
  - `hemograma`
  - `glicemia`
  - `perfil-lipidico`
- Results are simulated and include a reference range

## REST API

Base path: `/api/clinica`

### `POST /paciente`

Registers a patient.

Request body:

```json
{
  "firstName": "Ana",
  "lastName": "Lopez",
  "document": "CC-100",
  "email": "ana@example.com",
  "phone": "3001234567",
  "allergies": ["penicillin"]
}
```

Notes:

- Spanish aliases are also accepted for some fields, for example `nombres`, `apellidos`, `documento`, `correo`, `telefono`, `alergias`

### `GET /medicos?especialidad=cardiologia`

Returns doctors and currently available slots.

Supported specialty values:

- `cardiologia`
- `pediatria`
- `dermatologia`
- `medicina-general`

If `especialidad` is omitted, all doctors are returned.

### `POST /cita`

Schedules an appointment.

Request body:

```json
{
  "patientId": 1,
  "specialty": "cardiologia",
  "appointmentDate": "2026-04-12T09:00:00"
}
```

Important:

- `appointmentDate` must match one of the slots returned by `/medicos`
- Dates in the API use ISO-8601 format

### `GET /historia/{patientId}`

Returns the complete clinical history used by the frontend.

Response sections:

- `patient`
- `allergies`
- `consultations`
- `pastAppointments`
- `prescriptions`
- `laboratoryOrders`

### `POST /prescripcion`

Creates a prescription after validating allergies.

Request body:

```json
{
  "patientId": 1,
  "medications": [
    {
      "name": "loratadine",
      "dose": "10 mg",
      "duration": "7 days"
    }
  ]
}
```

### `POST /laboratorio`

Creates a laboratory order with simulated results.

Request body:

```json
{
  "patientId": 1,
  "exams": ["hemograma", "glicemia"]
}
```

## Frontend Integration Guidance

For the frontend agent, the recommended flow is:

1. Create a patient with `POST /api/clinica/paciente`
2. Fetch available doctors with `GET /api/clinica/medicos?especialidad=...`
3. Pick one of the returned slots and schedule it with `POST /api/clinica/cita`
4. Use `GET /api/clinica/historia/{patientId}` as the main consolidated patient dashboard source
5. Use `POST /api/clinica/prescripcion` and `POST /api/clinica/laboratorio` for secondary flows

The most useful endpoint for a dashboard is `GET /api/clinica/historia/{patientId}` because it already aggregates data from multiple subsystems.

## Error Behavior

The API currently uses Spring default error responses through `ResponseStatusException`.

Typical failure cases:

- `400 Bad Request`
  - Missing required fields
  - Unsupported specialty
  - Unsupported exam
  - Past appointment date
  - Medication blocked by allergies

- `404 Not Found`
  - Patient not found
  - Appointment not found

- `409 Conflict`
  - Duplicate patient document

## Tests

Integration tests live in `src/test/java/com/example/api/ApiApplicationTests.java`.

Current coverage validates:

- patient registration
- duplicate document rejection
- appointment scheduling
- allergy validation for prescriptions
- consolidated history response

## Known Limitations

- No persistence
- No authentication
- No pagination
- No update endpoints
- Appointment cancellation exists only at service level for now, not as a REST endpoint
- Past appointments only appear in history once their datetime is before the current system time
