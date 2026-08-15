package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.ConflictException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository branchRepository;
	private final TenantService tenantService;

	@Transactional
	public Branch create(String name, String address, String phone, String googleBusinessUrl) {
		PlanTier tier = tenantService.findByIdForUpdate(TenantContext.getTenantId()).getPlanTier();
		if (branchRepository.countByActiveTrue() >= tier.getMaxBranches()) {
			throw new BranchLimitExceededException(tier);
		}

		Branch branch = new Branch();
		branch.setName(name);
		branch.setAddress(address);
		branch.setPhone(phone);
		branch.setGoogleBusinessUrl(googleBusinessUrl);
		return branchRepository.save(branch);
	}

	@Transactional(readOnly = true)
	public List<Branch> findAll() {
		return branchRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Branch findById(UUID id) {
		return branchRepository.findById(id).orElseThrow(() -> new NotFoundException("Branch not found: " + id));
	}

	@Transactional
	public Branch update(UUID id, String name, String address, String phone, String googleBusinessUrl,
			boolean active) {
		Branch branch = findById(id);
		branch.setName(name);
		branch.setAddress(address);
		branch.setPhone(phone);
		branch.setGoogleBusinessUrl(googleBusinessUrl);
		branch.setActive(active);
		return branch;
	}

	/**
	 * Deletes the branch and, via ON DELETE CASCADE (see V28 migration), everything that only makes
	 * sense scoped to it: its professionals (and in turn each professional's own weekly hours,
	 * time-off, service assignments, waitlist entries), its own branch hours, and every appointment
	 * ever booked there (payments cascade with their appointment, sales just lose the appointment
	 * link — both per the V27 rules already in place). Irreversible — the confirmation the frontend
	 * shows before calling this is the only guard.
	 */
	@Transactional
	public void delete(UUID id) {
		if (!branchRepository.existsById(id)) {
			throw new NotFoundException("Branch not found: " + id);
		}
		try {
			branchRepository.deleteById(id);
			branchRepository.flush();
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException("No se pudo eliminar la sucursal. Probá de nuevo en unos segundos.");
		}
	}
}
