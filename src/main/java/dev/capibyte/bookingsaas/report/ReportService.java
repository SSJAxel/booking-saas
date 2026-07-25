package dev.capibyte.bookingsaas.report;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentService;
import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import dev.capibyte.bookingsaas.booking.PaymentStatus;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.payment.Payment;
import dev.capibyte.bookingsaas.payment.PaymentRepository;
import dev.capibyte.bookingsaas.report.dto.ProfessionalCount;
import dev.capibyte.bookingsaas.report.dto.ReportSummaryResponse;
import dev.capibyte.bookingsaas.report.dto.ServiceCount;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates in memory over AppointmentService.search()'s result rather than a SQL GROUP BY —
 * consistent with search()'s own approach, and fine at this project's scale; a real high-volume
 * deployment would push this down into the database instead.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

	private static final int TOP_N = 5;

	private final AppointmentService appointmentService;
	private final ServiceOfferingService serviceOfferingService;
	private final ProfessionalService professionalService;
	private final PaymentRepository paymentRepository;

	@Transactional(readOnly = true)
	public ReportSummaryResponse summarize(UUID branchId, UUID professionalId, Instant from, Instant to) {
		List<Appointment> appointments = appointmentService.search(branchId, professionalId, from, to, null);

		Map<AppointmentStatus, Long> byStatus = appointments.stream()
				.collect(Collectors.groupingBy(Appointment::getStatus, Collectors.counting()));

		Map<UUID, ServiceOffering> serviceCache = new HashMap<>();
		BigDecimal revenue = appointments.stream()
				.filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
				.map(a -> serviceCache.computeIfAbsent(a.getServiceId(), serviceOfferingService::findById).getPrice())
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		long completed = byStatus.getOrDefault(AppointmentStatus.COMPLETED, 0L);
		long noShows = byStatus.getOrDefault(AppointmentStatus.NO_SHOW, 0L);
		double noShowRate = (completed + noShows) == 0 ? 0.0 : (double) noShows / (completed + noShows);

		List<ServiceCount> topServices = appointments.stream()
				.collect(Collectors.groupingBy(Appointment::getServiceId, Collectors.counting()))
				.entrySet().stream()
				.sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
				.limit(TOP_N)
				.map(e -> new ServiceCount(e.getKey(),
						serviceCache.computeIfAbsent(e.getKey(), serviceOfferingService::findById).getName(), e.getValue()))
				.toList();

		List<ProfessionalCount> topProfessionals = appointments.stream()
				.collect(Collectors.groupingBy(Appointment::getProfessionalId, Collectors.counting()))
				.entrySet().stream()
				.sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
				.limit(TOP_N)
				.map(e -> new ProfessionalCount(e.getKey(), professionalService.findById(e.getKey()).getDisplayName(),
						e.getValue()))
				.toList();

		BigDecimal depositsCollected = paymentRepository.findAll().stream()
				.filter(p -> p.getStatus() == PaymentStatus.PAID)
				.filter(p -> from == null || !p.getCreatedAt().isBefore(from))
				.filter(p -> to == null || p.getCreatedAt().isBefore(to))
				.map(Payment::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new ReportSummaryResponse(appointments.size(), byStatus, revenue, depositsCollected, noShowRate,
				topServices, topProfessionals);
	}
}
