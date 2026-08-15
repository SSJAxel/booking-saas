package dev.capibyte.bookingsaas.report;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentService;
import dev.capibyte.bookingsaas.booking.AppointmentStatus;
import dev.capibyte.bookingsaas.booking.Client;
import dev.capibyte.bookingsaas.booking.PaymentStatus;
import dev.capibyte.bookingsaas.catalog.ServiceOffering;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.inventory.Sale;
import dev.capibyte.bookingsaas.inventory.SaleRepository;
import dev.capibyte.bookingsaas.payment.Payment;
import dev.capibyte.bookingsaas.payment.PaymentRepository;
import dev.capibyte.bookingsaas.report.dto.ClientStatsResponse;
import dev.capibyte.bookingsaas.report.dto.DailyCountResponse;
import dev.capibyte.bookingsaas.report.dto.DailySalesResponse;
import dev.capibyte.bookingsaas.report.dto.ProfessionalCount;
import dev.capibyte.bookingsaas.report.dto.ReportSummaryResponse;
import dev.capibyte.bookingsaas.report.dto.ServiceCount;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
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
	private final TenantService tenantService;
	private final SaleRepository saleRepository;

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

	/** Delegates straight to {@link #summarize} with today's bounds in the tenant's own zone —
	 * same reasoning as AppointmentService.findActiveByProfessionalAndDay: "today" has to mean
	 * the tenant's calendar day, not the server's, or a booking near midnight gets miscounted. */
	@Transactional(readOnly = true)
	public ReportSummaryResponse summarizeToday() {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		LocalDate today = LocalDate.now(zone);
		Instant from = today.atStartOfDay(zone).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
		return summarize(null, null, from, to);
	}

	@Transactional(readOnly = true)
	public List<DailyCountResponse> dailyAppointmentCounts(int days) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		LocalDate today = LocalDate.now(zone);
		LocalDate startDate = today.minusDays(days - 1L);
		Instant from = startDate.atStartOfDay(zone).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();

		Map<LocalDate, Long> countsByDay = appointmentService.search(null, null, from, to, null).stream()
				.collect(Collectors.groupingBy(a -> a.getStartTime().atZone(zone).toLocalDate(), Collectors.counting()));

		return startDate.datesUntil(today.plusDays(1))
				.map(date -> new DailyCountResponse(date, countsByDay.getOrDefault(date, 0L)))
				.toList();
	}

	/**
	 * Per-client rollup across the tenant's whole history (no date filter — a client who cancels a
	 * lot or comes back often is a pattern worth seeing regardless of which week it's viewed from).
	 * Sorted by total appointments descending; the frontend derives its own "cancela más" /
	 * "viene seguido" lists from completedCount/cancelledCount rather than this needing two
	 * separate sorted lists, since a client can appear in both.
	 */
	@Transactional(readOnly = true)
	public List<ClientStatsResponse> clientStats() {
		Map<UUID, List<Appointment>> byClient = appointmentService.search(null, null, null, null, null).stream()
				.collect(Collectors.groupingBy(Appointment::getClientId));

		return byClient.entrySet().stream()
				.map(e -> {
					List<Appointment> clientAppointments = e.getValue();
					Client client = appointmentService.findClient(e.getKey());
					Map<AppointmentStatus, Long> byStatus = clientAppointments.stream()
							.collect(Collectors.groupingBy(Appointment::getStatus, Collectors.counting()));
					return new ClientStatsResponse(client.getId(), client.getName(), client.getEmail(),
							clientAppointments.size(), byStatus.getOrDefault(AppointmentStatus.COMPLETED, 0L),
							byStatus.getOrDefault(AppointmentStatus.CANCELLED, 0L),
							byStatus.getOrDefault(AppointmentStatus.NO_SHOW, 0L), client.getRating(),
							client.isPinned(), client.getNotes());
				})
				.sorted(Comparator.comparingLong(ClientStatsResponse::totalAppointments).reversed())
				.toList();
	}

	@Transactional(readOnly = true)
	public List<DailySalesResponse> dailySales(int days) {
		ZoneId zone = tenantService.getZoneId(TenantContext.getTenantId());
		LocalDate today = LocalDate.now(zone);
		LocalDate startDate = today.minusDays(days - 1L);
		Instant from = startDate.atStartOfDay(zone).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();

		Map<LocalDate, BigDecimal> amountByDay = saleRepository.findAll().stream()
				.filter(s -> !s.getCreatedAt().isBefore(from) && s.getCreatedAt().isBefore(to))
				.collect(Collectors.groupingBy(s -> s.getCreatedAt().atZone(zone).toLocalDate(),
						Collectors.reducing(BigDecimal.ZERO, Sale::getAmount, BigDecimal::add)));

		return startDate.datesUntil(today.plusDays(1))
				.map(date -> new DailySalesResponse(date, amountByDay.getOrDefault(date, BigDecimal.ZERO)))
				.toList();
	}
}
