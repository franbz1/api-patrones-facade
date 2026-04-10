package com.example.api.clinic;

import com.example.api.auth.PatientPrincipal;
import com.example.api.clinic.ClinicDomain.Appointment;
import com.example.api.clinic.ClinicDomain.CompleteHistoryResponse;
import com.example.api.clinic.ClinicDomain.CreateAppointmentRequest;
import com.example.api.clinic.ClinicDomain.CreateLaboratoryRequest;
import com.example.api.clinic.ClinicDomain.CreatePatientRequest;
import com.example.api.clinic.ClinicDomain.CreatePrescriptionRequest;
import com.example.api.clinic.ClinicDomain.DoctorAvailability;
import com.example.api.clinic.ClinicDomain.LaboratoryOrder;
import com.example.api.clinic.ClinicDomain.Patient;
import com.example.api.clinic.ClinicDomain.Prescription;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinica")
public class ClinicController {

    private final ClinicFacade clinicFacade;
    private final AgendaService agendaService;

    public ClinicController(ClinicFacade clinicFacade, AgendaService agendaService) {
        this.clinicFacade = clinicFacade;
        this.agendaService = agendaService;
    }

    @PostMapping("/paciente")
    @ResponseStatus(HttpStatus.CREATED)
    public Patient createPatient(@RequestBody CreatePatientRequest request) {
        return clinicFacade.registerPatient(request);
    }

    @PostMapping("/cita")
    @ResponseStatus(HttpStatus.CREATED)
    public Appointment createAppointment(@RequestBody CreateAppointmentRequest request, Authentication authentication) {
        validatePatientAccess(authentication, request.patientId());
        return clinicFacade.agendarCita(request);
    }

    @GetMapping("/historia/{patientId}")
    public CompleteHistoryResponse getCompleteHistory(@PathVariable Long patientId, Authentication authentication) {
        validatePatientAccess(authentication, patientId);
        return clinicFacade.verHistoriaCompleta(patientId);
    }

    @PostMapping("/prescripcion")
    @ResponseStatus(HttpStatus.CREATED)
    public Prescription createPrescription(@RequestBody CreatePrescriptionRequest request, Authentication authentication) {
        validatePatientAccess(authentication, request.patientId());
        return clinicFacade.generarPrescripcion(request);
    }

    @PostMapping("/laboratorio")
    @ResponseStatus(HttpStatus.CREATED)
    public LaboratoryOrder createLaboratoryOrder(@RequestBody CreateLaboratoryRequest request, Authentication authentication) {
        validatePatientAccess(authentication, request.patientId());
        return clinicFacade.solicitarExamenes(request);
    }

    @GetMapping("/medicos")
    public List<DoctorAvailability> getDoctors(@RequestParam(required = false) String especialidad) {
        return agendaService.listAvailableDoctors(especialidad);
    }

    private void validatePatientAccess(Authentication authentication, Long patientId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PatientPrincipal principal)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        if (patientId == null) {
            return;
        }
        if (!principal.patientId().equals(patientId)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own patient data");
        }
    }
}
