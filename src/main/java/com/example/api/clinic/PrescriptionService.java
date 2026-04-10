package com.example.api.clinic;

import com.example.api.clinic.ClinicDomain.MedicationRequest;
import com.example.api.clinic.ClinicDomain.PrescribedMedication;
import com.example.api.clinic.ClinicDomain.Prescription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrescriptionService {

    private final AtomicLong prescriptionSequence = new AtomicLong(0);
    private final Map<Long, List<Prescription>> prescriptionsByPatient = new ConcurrentHashMap<>();

    public Prescription generatePrescription(Long patientId, List<MedicationRequest> medications, List<String> allergies) {
        if (medications == null || medications.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one medication is required");
        }

        List<String> conflictingMedications = medications.stream()
                .map(MedicationRequest::name)
                .filter(medicationName -> ClinicDomain.containsIgnoreCase(allergies, medicationName))
                .distinct()
                .toList();

        if (!conflictingMedications.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prescription contains medications blocked by allergies: " + String.join(", ", conflictingMedications));
        }

        List<PrescribedMedication> prescribedMedications = medications.stream()
                .map(this::toPrescribedMedication)
                .toList();

        Prescription prescription = new Prescription(
                prescriptionSequence.incrementAndGet(),
                patientId,
                LocalDateTime.now(),
                prescribedMedications,
                "Allergy validation completed successfully.");

        prescriptionsByPatient
                .computeIfAbsent(patientId, ignored -> new CopyOnWriteArrayList<>())
                .add(prescription);

        return prescription;
    }

    public List<Prescription> getPrescriptions(Long patientId) {
        return prescriptionsByPatient.getOrDefault(patientId, List.of()).stream().toList();
    }

    private PrescribedMedication toPrescribedMedication(MedicationRequest medicationRequest) {
        return new PrescribedMedication(
                ClinicDomain.requireText(medicationRequest.name(), "medication.name"),
                ClinicDomain.requireText(medicationRequest.dose(), "medication.dose"),
                ClinicDomain.requireText(medicationRequest.duration(), "medication.duration"));
    }
}
