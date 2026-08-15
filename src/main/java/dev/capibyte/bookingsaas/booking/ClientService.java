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
import java.util.List;
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

	@Transactional
	public Client updateNotes(UUID id, String notes) {
		Client client = findById(id);
		client.setNotes(notes);
		return client;
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

	private Client findById(UUID id) {
		return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client not found: " + id));
	}
}
