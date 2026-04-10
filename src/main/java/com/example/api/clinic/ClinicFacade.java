package com.example.api.clinic;

import com.example.api.auth.AuthService;
import com.example.api.clinic.ClinicDomain.Appointment;
import com.example.api.clinic.ClinicDomain.CompleteHistoryResponse;
import com.example.api.clinic.ClinicDomain.CreateAppointmentRequest;
import com.example.api.clinic.ClinicDomain.CreateLaboratoryRequest;
import com.example.api.clinic.ClinicDomain.CreatePatientRequest;
import com.example.api.clinic.ClinicDomain.CreatePrescriptionRequest;
import com.example.api.clinic.ClinicDomain.LaboratoryOrder;
import com.example.api.clinic.ClinicDomain.Patient;
import com.example.api.clinic.ClinicDomain.Prescription;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class ClinicFacade {

    private final PatientService patientService;
    private final AgendaService agendaService;
    private final MedicalRecordService medicalRecordService;
    private final PrescriptionService prescriptionService;
    private final LaboratoryService laboratoryService;
    private final AuthService authService;

    public ClinicFacade(
            PatientService patientService,
            AgendaService agendaService,
            MedicalRecordService medicalRecordService,
            PrescriptionService prescriptionService,
            LaboratoryService laboratoryService,
            AuthService authService) {
        this.patientService = patientService;
        this.agendaService = agendaService;
        this.medicalRecordService = medicalRecordService;
        this.prescriptionService = prescriptionService;
        this.laboratoryService = laboratoryService;
        this.authService = authService;
    }

    public Patient registerPatient(CreatePatientRequest request) {
        String password = ClinicDomain.requireText(request.password(), "password");
        Patient patient = patientService.registerPatient(request);
        authService.registerPatientAccess(patient, password);
        medicalRecordService.initializeRecord(patient.id());
        return patient;
    }

    public Appointment agendarCita(Long patientId, String specialty, java.time.LocalDateTime appointmentDate) {
        patientService.getPatientProfile(patientId);
        return agendaService.scheduleAppointment(patientId, specialty, appointmentDate);
    }

    public Appointment agendarCita(CreateAppointmentRequest request) {
        return agendarCita(request.patientId(), request.specialty(), request.appointmentDate());
    }

    public CompleteHistoryResponse verHistoriaCompleta(Long patientId) {
        Patient patient = patientService.getPatientProfile(patientId);
        return new CompleteHistoryResponse(
                patient,
                patient.allergies(),
                medicalRecordService.getConsultations(patientId),
                agendaService.getPastAppointments(patientId),
                prescriptionService.getPrescriptions(patientId),
                laboratoryService.getOrders(patientId));
    }

    public Prescription generarPrescripcion(Long patientId, java.util.List<ClinicDomain.MedicationRequest> medications) {
        Patient patient = patientService.getPatientProfile(patientId);
        Prescription prescription = prescriptionService.generatePrescription(patientId, medications, patient.allergies());
        medicalRecordService.registerConsultation(
                patientId,
                "Prescription issued",
                "Medication follow-up",
                LocalDate.now());
        return prescription;
    }

    public Prescription generarPrescripcion(CreatePrescriptionRequest request) {
        return generarPrescripcion(request.patientId(), request.medications());
    }

    public LaboratoryOrder solicitarExamenes(Long patientId, java.util.List<String> exams) {
        patientService.getPatientProfile(patientId);
        LaboratoryOrder laboratoryOrder = laboratoryService.requestExams(patientId, exams);
        medicalRecordService.registerConsultation(
                patientId,
                "Laboratory exams requested",
                "Pending laboratory review",
                LocalDate.now());
        return laboratoryOrder;
    }

    public LaboratoryOrder solicitarExamenes(CreateLaboratoryRequest request) {
        return solicitarExamenes(request.patientId(), request.exams());
    }
}
