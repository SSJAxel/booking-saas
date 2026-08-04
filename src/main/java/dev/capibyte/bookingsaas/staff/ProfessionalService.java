package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.tenant.BranchService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfessionalService {

	private final ProfessionalRepository professionalRepository;
	private final BranchService branchService;

	@Transactional
	public Professional create(UUID branchId, String displayName, String bio) {
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

	@Transactional
	public void delete(UUID id) {
		if (!professionalRepository.existsById(id)) {
			throw new NotFoundException("Professional not found: " + id);
		}
		professionalRepository.deleteById(id);
	}
}
