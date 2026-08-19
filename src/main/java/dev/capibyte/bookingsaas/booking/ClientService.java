package dev.capibyte.bookingsaas.booking;

import dev.capibyte.bookingsaas.booking.dto.ClientVisitResponse;
import dev.capibyte.bookingsaas.catalog.ServiceOfferingService;
import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import dev.capibyte.bookingsaas.tenant.RewardTier;
import dev.capibyte.bookingsaas.tenant.RewardTierRepository;
import dev.capibyte.bookingsaas.tenant.Tenant;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

	private final ClientRepository clientRepository;
	private final AppointmentRepository appointmentRepository;
	private final ServiceOfferingService serviceOfferingService;
	private final ProfessionalService professionalService;
	private final TenantService tenantService;
	private final RewardTierRepository rewardTierRepository;

	@Transactional(readOnly = true)
	public List<Client> search(String query) {
		return clientRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
	}

	/** "Cliente fijo": kept in the Mejores clientes panel regardless of {@code rating} — see
	 * Client's Javadoc for why a tenant would want this. */
	@Transactional
	public Client setPinned(UUID id, boolean pinned) {
		Client client = findById(id);
		client.setPinned(pinned);
		return client;
	}

	/** "Ficha del cliente": notas generales, preferencias técnicas del servicio, y alergias — las
	 * tres siempre cargadas por quien presta el servicio, nunca por el cliente. Un solo endpoint
	 * (no tres) porque en la práctica se editan juntas desde el mismo formulario del panel.
	 * {@code notes} es libre en todos los planes (existía antes de este gate); {@code
	 * servicePreferences}/{@code allergies} son PRO/MAX — mismo patrón "borrar siempre se puede,
	 * cargar un valor real está gateado" que el mensaje de cumpleaños: un tenant que bajó de plan
	 * después de haber cargado datos los sigue viendo, solo no puede escribir nuevos. Solo bloquea
	 * cuando el valor efectivamente CAMBIA a algo nuevo — un formulario que reenvía el mismo
	 * servicePreferences/allergies de siempre (ej. el dueño solo tocó notes) no debe romperse para
	 * un tenant ya bajado de plan. */
	@Transactional
	public Client updateProfile(UUID id, String notes, String servicePreferences, String allergies) {
		Client client = findById(id);
		boolean changesServicePreferences = !Objects.equals(servicePreferences, client.getServicePreferences());
		boolean changesAllergies = !Objects.equals(allergies, client.getAllergies());
		if ((changesServicePreferences && hasText(servicePreferences)) || (changesAllergies && hasText(allergies))) {
			Tenant tenant = tenantService.findById(TenantContext.getTenantId());
			if (!tenant.getPlanTier().isClientProfileEnabled()) {
				throw new BadRequestException(
						"Plan " + tenant.getPlanTier() + " doesn't include service preferences/allergies");
			}
		}
		client.setNotes(notes);
		client.setServicePreferences(servicePreferences);
		client.setAllergies(allergies);
		return client;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@Transactional
	public Client updateBirthDate(UUID id, LocalDate birthDate) {
		Client client = findById(id);
		client.setBirthDate(birthDate);
		return client;
	}

	/** "Cumpleaños del mes" panel list (PRO/MAX) — see PlanTier's Javadoc for why this is a passive
	 * list and not an automated discount. */
	@Transactional(readOnly = true)
	public List<Client> birthdaysThisMonth() {
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		if (!tenant.getPlanTier().isBirthdayRemindersEnabled()) {
			throw new BadRequestException("Plan " + tenant.getPlanTier() + " doesn't include birthday reminders");
		}
		int month = LocalDate.now(tenantService.getZoneId(tenant.getId())).getMonthValue();
		return clientRepository.findByBirthMonth(month);
	}

	/** Every visit this client has ever had with the tenant, most recent first — see the "who did
	 * they see, when" ask this backs (ClientHistoryModal.jsx on the frontend). */
	@Transactional(readOnly = true)
	public List<ClientVisitResponse> history(UUID id) {
		findById(id); // 404s if missing or belongs to another tenant, before touching appointments
		return appointmentRepository.findByClientIdOrderByStartTimeDesc(id).stream()
				.map(a -> new ClientVisitResponse(a.getId(), a.getStartTime(),
						serviceOfferingService.findById(a.getServiceId()).getName(),
						professionalService.findById(a.getProfessionalId()).getDisplayName(), a.getStatus()))
				.toList();
	}

	/** Spends (not resets) points toward one specific tier the client chooses — a client sitting on
	 * more points than that tier costs keeps the remainder banked toward whichever tier they pick
	 * next time, rather than losing it. See RewardTier's Javadoc for the overall design. */
	@Transactional
	public Client redeemReward(UUID clientId, UUID rewardTierId) {
		Tenant tenant = tenantService.findById(TenantContext.getTenantId());
		if (!tenant.isLoyaltyRewardsEnabled()) {
			throw new BadRequestException("Loyalty rewards aren't enabled for this tenant");
		}
		RewardTier tier = rewardTierRepository.findById(rewardTierId)
				.orElseThrow(() -> new NotFoundException("Reward tier not found: " + rewardTierId));
		Client client = findById(clientId);
		if (client.getLoyaltyPoints() < tier.getPointsRequired()) {
			throw new BadRequestException(
					"Client only has " + client.getLoyaltyPoints() + " points, this tier needs " + tier.getPointsRequired());
		}
		client.setLoyaltyPoints(client.getLoyaltyPoints() - tier.getPointsRequired());
		return client;
	}

	/**
	 * Borrado manual e irreversible del cliente entero — turnos, pagos (vía el cascade ya existente
	 * turno→pago de V27), reseñas y puntos de fidelidad desaparecen con él (V46 agrega el cascade
	 * necesario a nivel de base; antes de eso esto fallaba con una violación de foreign key apenas
	 * el cliente tuviera algún turno real, que en la práctica era siempre). No toca ventas de
	 * mostrador ya registradas (sales.appointment_id se pone en null, igual que al borrar un turno
	 * suelto — V27) ni waitlist_entries (no referencia client_id, guarda nombre/email/teléfono en el
	 * momento). Confirmación de "esto es irreversible" es responsabilidad del panel, no de acá.
	 */
	@Transactional
	public void delete(UUID id) {
		if (!clientRepository.existsById(id)) {
			throw new NotFoundException("Client not found: " + id);
		}
		clientRepository.deleteById(id);
	}

	private Client findById(UUID id) {
		return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client not found: " + id));
	}
}
