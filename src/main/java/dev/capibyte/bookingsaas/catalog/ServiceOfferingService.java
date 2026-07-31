package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.staff.ProfessionalService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

	private final ServiceOfferingRepository serviceOfferingRepository;
	private final ProfessionalServiceAssignmentRepository assignmentRepository;
	private final ProfessionalService professionalService;

	@Transactional
	public ServiceOffering create(String name, String description, int durationMinutes, BigDecimal price,
			BigDecimal depositAmount) {
		ServiceOffering service = new ServiceOffering();
		service.setName(name);
		service.setDescription(description);
		service.setDurationMinutes(durationMinutes);
		service.setPrice(price);
		service.setDepositAmount(depositAmount);
		return serviceOfferingRepository.save(service);
	}

	@Transactional(readOnly = true)
	public List<ServiceOffering> findAll() {
		return serviceOfferingRepository.findAll();
	}

	@Transactional(readOnly = true)
	public ServiceOffering findById(UUID id) {
		return serviceOfferingRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Service not found: " + id));
	}

	@Transactional
	public ServiceOffering update(UUID id, String name, String description, int durationMinutes, BigDecimal price,
			BigDecimal depositAmount, boolean active) {
		ServiceOffering service = findById(id);
		service.setName(name);
		service.setDescription(description);
		service.setDurationMinutes(durationMinutes);
		service.setPrice(price);
		service.setDepositAmount(depositAmount);
		service.setActive(active);
		return service;
	}

	@Transactional
	public void delete(UUID id) {
		if (!serviceOfferingRepository.existsById(id)) {
			throw new NotFoundException("Service not found: " + id);
		}
		serviceOfferingRepository.deleteById(id);
	}

	@Transactional
	public void assignProfessional(UUID serviceId, UUID professionalId) {
		findById(serviceId);
		professionalService.findById(professionalId);
		if (assignmentRepository.findByProfessionalIdAndServiceId(professionalId, serviceId).isPresent()) {
			return;
		}
		ProfessionalServiceAssignment assignment = new ProfessionalServiceAssignment();
		assignment.setProfessionalId(professionalId);
		assignment.setServiceId(serviceId);
		assignmentRepository.save(assignment);
	}

	@Transactional
	public void unassignProfessional(UUID serviceId, UUID professionalId) {
		assignmentRepository.findByProfessionalIdAndServiceId(professionalId, serviceId)
				.ifPresent(assignmentRepository::delete);
	}

	@Transactional(readOnly = true)
	public List<UUID> findProfessionalIdsForService(UUID serviceId) {
		findById(serviceId);
		return assignmentRepository.findAllByServiceId(serviceId).stream()
				.map(ProfessionalServiceAssignment::getProfessionalId).toList();
	}

	/**
	 * A service isn't itself branch-scoped (the catalog is tenant-wide) — whether it's "offered at"
	 * a branch is derived from whether any active professional there is assigned to it. Used by the
	 * public API to filter the service list once a client picks a branch (PublicBookingController).
	 */
	@Transactional(readOnly = true)
	public boolean isOfferedAtBranch(UUID serviceId, UUID branchId) {
		return findProfessionalIdsForService(serviceId).stream()
				.map(professionalService::findById)
				.anyMatch(p -> p.isActive() && p.getBranchId().equals(branchId));
	}
}
