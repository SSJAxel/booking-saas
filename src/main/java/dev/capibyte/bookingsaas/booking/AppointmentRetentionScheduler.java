package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Auto-purga (DELETE real, irreversible) los turnos más viejos que el límite de retención que cada
 * tenant configuró (Tenant#historyRetentionMonths, 1–12 meses) — nunca toca el Client asociado (ver
 * V27's ON DELETE CASCADE/SET NULL para payments/sales). Mismo patrón de loop manual con
 * TenantContext que PendingDepositExpirationScheduler, por la misma razón: Appointment es
 * @TenantId-scoped y esto corre fuera de un request/JWT. A diferencia de ese scheduler, el corte NO
 * es un valor global — cada tenant tiene su propio historyRetentionMonths, así que se recalcula
 * DENTRO del loop, por tenant. El borrado en sí delega en
 * AppointmentService#purgeHistoryOlderThan (no llama al repository directo): un delete derivado
 * necesita una transacción activa, que un método de un @Component plano no abre por sí solo.
 */
@Component
public class AppointmentRetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(AppointmentRetentionScheduler.class);

	private final TenantRepository tenantRepository;
	private final AppointmentService appointmentService;

	public AppointmentRetentionScheduler(TenantRepository tenantRepository, AppointmentService appointmentService) {
		this.tenantRepository = tenantRepository;
		this.appointmentService = appointmentService;
	}

	@Scheduled(fixedDelayString = "${app.booking.retention-check-interval-ms:86400000}")
	public void purgeExpiredHistory() {
		for (Tenant tenant : tenantRepository.findAll()) {
			TenantContext.setTenantId(tenant.getId());
			try {
				// Instant no soporta MONTHS directo (no es una unidad calendárica) — hay que pasar
				// por un tipo que sí las entienda y volver. UTC fijo: esto es una ventana de
				// duración aproximada, no un corte alineado al huso horario del negocio.
				Instant cutoff = Instant.now().atZone(ZoneOffset.UTC)
						.minusMonths(tenant.getHistoryRetentionMonths())
						.toInstant();
				long deleted = appointmentService.purgeHistoryOlderThan(cutoff);
				if (deleted > 0) {
					log.info("Retention purge for tenant {}: deleted {} appointments older than {} months",
							tenant.getId(), deleted, tenant.getHistoryRetentionMonths());
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
