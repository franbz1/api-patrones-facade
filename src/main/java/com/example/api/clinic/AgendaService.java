package com.example.api.clinic;

import com.example.api.clinic.ClinicDomain.Appointment;
import com.example.api.clinic.ClinicDomain.AppointmentStatus;
import com.example.api.clinic.ClinicDomain.DoctorAvailability;
import com.example.api.clinic.ClinicDomain.Specialty;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgendaService {

    private static final String DEFAULT_REMINDER = "Arrive 15 minutes early and bring your ID document.";

    private final AtomicLong appointmentSequence = new AtomicLong(0);
    private final Map<Long, DoctorSchedule> schedulesByDoctorId = new LinkedHashMap<>();
    private final Map<Long, Appointment> appointmentsById = new ConcurrentHashMap<>();

    public AgendaService() {
        seedDoctors();
    }

    public List<DoctorAvailability> listAvailableDoctors(String specialtyValue) {
        Specialty specialty = ClinicDomain.hasAnyText(specialtyValue) ? Specialty.fromValue(specialtyValue) : null;

        return schedulesByDoctorId.values().stream()
                .filter(schedule -> specialty == null || schedule.specialty == specialty)
                .map(this::toDoctorAvailability)
                .toList();
    }

    public Appointment scheduleAppointment(Long patientId, String specialtyValue, LocalDateTime appointmentDate) {
        if (appointmentDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointmentDate is required");
        }
        if (appointmentDate.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointmentDate must be in the future");
        }

        Specialty specialty = Specialty.fromValue(specialtyValue);
        LocalDateTime normalizedDate = appointmentDate.truncatedTo(ChronoUnit.MINUTES);

        for (DoctorSchedule schedule : schedulesByDoctorId.values()) {
            if (schedule.specialty != specialty) {
                continue;
            }
            if (!schedule.availableSlots.contains(normalizedDate)) {
                continue;
            }

            schedule.availableSlots.remove(normalizedDate);
            return storeAppointment(patientId, schedule, normalizedDate);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No available doctor found for the requested specialty and date");
    }

    public Appointment createHistoricalAppointment(Long patientId, String specialtyValue, LocalDateTime appointmentDate) {
        if (appointmentDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "appointmentDate is required");
        }
        if (!appointmentDate.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "historical appointment must be in the past");
        }

        Specialty specialty = Specialty.fromValue(specialtyValue);
        DoctorSchedule schedule = schedulesByDoctorId.values().stream()
                .filter(candidate -> candidate.specialty == specialty)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported specialty"));

        return storeAppointment(patientId, schedule, appointmentDate.truncatedTo(ChronoUnit.MINUTES));
    }

    public Appointment cancelAppointment(Long appointmentId) {
        Appointment appointment = appointmentsById.get(appointmentId);
        if (appointment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        if (appointment.status() == AppointmentStatus.CANCELLED) {
            return appointment;
        }

        DoctorSchedule schedule = schedulesByDoctorId.get(appointment.doctorId());
        schedule.availableSlots.add(appointment.appointmentDate());
        schedule.availableSlots.sort(Comparator.naturalOrder());

        Appointment cancelledAppointment = new Appointment(
                appointment.id(),
                appointment.patientId(),
                appointment.doctorId(),
                appointment.doctorName(),
                appointment.specialty(),
                appointment.appointmentDate(),
                AppointmentStatus.CANCELLED,
                appointment.reminder());

        appointmentsById.put(cancelledAppointment.id(), cancelledAppointment);
        return cancelledAppointment;
    }

    public List<Appointment> getPastAppointments(Long patientId) {
        LocalDateTime now = LocalDateTime.now();
        return appointmentsById.values().stream()
                .filter(appointment -> appointment.patientId().equals(patientId))
                .filter(appointment -> appointment.status() == AppointmentStatus.SCHEDULED)
                .filter(appointment -> appointment.appointmentDate().isBefore(now))
                .sorted(Comparator.comparing(Appointment::appointmentDate).reversed())
                .toList();
    }

    private DoctorAvailability toDoctorAvailability(DoctorSchedule schedule) {
        return new DoctorAvailability(
                schedule.id,
                schedule.fullName,
                schedule.specialty.apiValue(),
                schedule.availableSlots.stream()
                        .sorted()
                        .toList());
    }

    private Appointment storeAppointment(Long patientId, DoctorSchedule schedule, LocalDateTime appointmentDate) {
        Appointment appointment = new Appointment(
                appointmentSequence.incrementAndGet(),
                patientId,
                schedule.id,
                schedule.fullName,
                schedule.specialty.apiValue(),
                appointmentDate,
                AppointmentStatus.SCHEDULED,
                DEFAULT_REMINDER);

        appointmentsById.put(appointment.id(), appointment);
        return appointment;
    }

    private void seedDoctors() {
        addDoctor(1L, "Dr. Laura Gomez", Specialty.CARDIOLOGY);
        addDoctor(2L, "Dr. Nicolas Herrera", Specialty.CARDIOLOGY);
        addDoctor(3L, "Dr. Sofia Martinez", Specialty.DERMATOLOGY);
        addDoctor(4L, "Dr. Andres Ruiz", Specialty.PEDIATRICS);
        addDoctor(5L, "Dr. Camila Torres", Specialty.GENERAL_MEDICINE);
    }

    private void addDoctor(Long id, String fullName, Specialty specialty) {
        List<LocalDateTime> slots = new ArrayList<>();
        slots.add(nextSlot(1, 9));
        slots.add(nextSlot(2, 11));
        slots.add(nextSlot(3, 14));
        schedulesByDoctorId.put(id, new DoctorSchedule(id, fullName, specialty, slots));
    }

    private LocalDateTime nextSlot(long dayOffset, int hour) {
        return LocalDateTime.now()
                .plusDays(dayOffset)
                .withHour(hour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    private static final class DoctorSchedule {
        private final Long id;
        private final String fullName;
        private final Specialty specialty;
        private final List<LocalDateTime> availableSlots;

        private DoctorSchedule(Long id, String fullName, Specialty specialty, List<LocalDateTime> availableSlots) {
            this.id = id;
            this.fullName = fullName;
            this.specialty = specialty;
            this.availableSlots = availableSlots;
        }
    }
}
