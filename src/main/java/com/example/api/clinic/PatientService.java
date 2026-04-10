package com.example.api.clinic;

import com.example.api.clinic.ClinicDomain.CreatePatientRequest;
import com.example.api.clinic.ClinicDomain.Patient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientService {

    private final AtomicLong patientSequence = new AtomicLong(0);
    private final Map<Long, Patient> patientsById = new ConcurrentHashMap<>();
    private final Map<String, Long> patientIdsByDocument = new ConcurrentHashMap<>();

    public Patient registerPatient(CreatePatientRequest request) {
        String firstName = ClinicDomain.requireText(request.firstName(), "firstName");
        String lastName = ClinicDomain.requireText(request.lastName(), "lastName");
        String document = ClinicDomain.requireText(request.document(), "document");
        String email = ClinicDomain.requireText(request.email(), "email");
        String phone = ClinicDomain.requireText(request.phone(), "phone");
        String normalizedDocument = ClinicDomain.normalize(document);

        if (patientIdsByDocument.containsKey(normalizedDocument)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document already exists");
        }

        Long patientId = patientSequence.incrementAndGet();
        Patient patient = new Patient(
                patientId,
                firstName,
                lastName,
                document,
                email,
                phone,
                request.allergies());

        patientsById.put(patientId, patient);
        patientIdsByDocument.put(normalizedDocument, patientId);
        return patient;
    }

    public Patient getPatientProfile(Long patientId) {
        if (patientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        Patient patient = patientsById.get(patientId);
        if (patient == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found");
        }
        return patient;
    }
}
