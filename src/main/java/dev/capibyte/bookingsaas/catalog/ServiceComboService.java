package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceComboService {

	private final ServiceComboRepository serviceComboRepository;
	private final ServiceOfferingService serviceOfferingService;
	private final TenantService tenantService;

	@Transactional
	public ServiceCombo create(UUID serviceAId, UUID serviceBId, BigDecimal comboPrice, BigDecimal comboDepositAmount) {
		requirePlanSupport();
		if (serviceAId.equals(serviceBId)) {
			throw new BadRequestException("A combo needs two different services");
		}
		serviceOfferingService.findById(serviceAId);
		serviceOfferingService.findById(serviceBId);
		UUID[] pair = canonicalPair(serviceAId, serviceBId);

		ServiceCombo combo = new ServiceCombo();
		combo.setServiceAId(pair[0]);
		combo.setServiceBId(pair[1]);
		combo.setComboPrice(comboPrice);
		combo.setComboDepositAmount(comboDepositAmount);
		try {
			return serviceComboRepository.saveAndFlush(combo);
		} catch (DataIntegrityViolationException ex) {
			throw new BadRequestException("A combo for these two services already exists");
		}
	}

	@Transactional(readOnly = true)
	public List<ServiceCombo> findAll() {
		return serviceComboRepository.findAll();
	}

	@Transactional
	public ServiceCombo update(UUID id, BigDecimal comboPrice, BigDecimal comboDepositAmount, boolean active) {
		requirePlanSupport();
		ServiceCombo combo = findById(id);
		combo.setComboPrice(comboPrice);
		combo.setComboDepositAmount(comboDepositAmount);
		combo.setActive(active);
		return combo;
	}

	@Transactional
	public void delete(UUID id) {
		if (!serviceComboRepository.existsById(id)) {
			throw new NotFoundException("Service combo not found: " + id);
		}
		serviceComboRepository.deleteById(id);
	}

	/**
	 * The only lookup that matters for actually applying a discount — used both by the public
	 * preview endpoint and by {@code AppointmentService#bookGroup} itself (the source of truth for
	 * what gets charged). Re-checks the plan every call, not just at creation time, the same
	 * reasoning {@code AppointmentService#publishNotification} already documents for
	 * {@code whatsappEnabled}: a combo row can outlive a downgrade away from MAX, and a downgraded
	 * tenant must stop getting the discount applied even though old rows are still sitting in the
	 * table.
	 */
	@Transactional(readOnly = true)
	public Optional<ServiceCombo> findApplicableCombo(UUID serviceAId, UUID serviceBId) {
		PlanTier tier = tenantService.findById(TenantContext.getTenantId()).getPlanTier();
		if (!tier.isServiceCombosEnabled()) {
			return Optional.empty();
		}
		UUID[] pair = canonicalPair(serviceAId, serviceBId);
		return serviceComboRepository.findByServiceAIdAndServiceBIdAndActiveTrue(pair[0], pair[1]);
	}

	private ServiceCombo findById(UUID id) {
		return serviceComboRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Service combo not found: " + id));
	}

	private void requirePlanSupport() {
		PlanTier tier = tenantService.findById(TenantContext.getTenantId()).getPlanTier();
		if (!tier.isServiceCombosEnabled()) {
			throw new BadRequestException("Plan " + tier + " doesn't include service combos");
		}
	}

	/** UUID has no business meaning to order by — this only needs to be a *consistent* order so the
	 * same pair always normalizes to the same row, regardless of which one the caller called "A". */
	private UUID[] canonicalPair(UUID serviceAId, UUID serviceBId) {
		return serviceAId.compareTo(serviceBId) <= 0 ? new UUID[] { serviceAId, serviceBId }
				: new UUID[] { serviceBId, serviceAId };
	}
}
