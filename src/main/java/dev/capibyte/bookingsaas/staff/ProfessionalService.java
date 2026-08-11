package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.ConflictException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.BranchService;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfessionalService {

	private final ProfessionalRepository professionalRepository;
	private final BranchService branchService;
	private final TenantService tenantService;

	@Transactional
	public Professional create(UUID branchId, String displayName, String bio) {
		PlanTier tier = tenantService.findById(TenantContext.getTenantId()).getPlanTier();
		if (professionalRepository.countByActiveTrue() >= tier.getMaxProfessionals()) {
			throw new ProfessionalLimitExceededException(tier);
		}
		branchService.findById(branchId); // 404s (not FK violation) if missing or belongs to another tenant
		Professional professional = new Professional();
		professional.setBranchId(branchId);
		professional.setDisplayName(displayName);
		professional.setBio(bio);
		return professionalRepository.save(professional);
	}

	@Transactional(readOnly = true)
	public List<Professional> findAll() {
		return professionalRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Professional findById(UUID id) {
		return professionalRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Professional not found: " + id));
	}

	@Transactional
	public Professional update(UUID id, UUID branchId, String displayName, String bio, boolean active) {
		branchService.findById(branchId); // 404s (not FK violation) if missing or belongs to another tenant
		Professional professional = findById(id);
		professional.setBranchId(branchId);
		professional.setDisplayName(displayName);
		professional.setBio(bio);
		professional.setActive(active);
		return professional;
	}

	/**
	 * Deletes the professional and, via ON DELETE CASCADE (see V28 migration), everything that only
	 * makes sense scoped to them: weekly hours, time-off, service assignments, waitlist entries, and
	 * every appointment ever booked with them (payments cascade with their appointment, sales just
	 * lose the appointment link — both per the V27 rules already in place). Irreversible — the
	 * confirmation the frontend shows before calling this is the only guard.
	 */
	@Transactional
	public void delete(UUID id) {
		if (!professionalRepository.existsById(id)) {
			throw new NotFoundException("Professional not found: " + id);
		}
		try {
			professionalRepository.deleteById(id);
			professionalRepository.flush();
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException("No se pudo eliminar el profesional. Probá de nuevo en unos segundos.");
		}
	}
}
