package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.catalog.dto.AssignProfessionalRequest;
import dev.capibyte.bookingsaas.catalog.dto.ServiceOfferingRequest;
import dev.capibyte.bookingsaas.catalog.dto.ServiceOfferingResponse;
import dev.capibyte.bookingsaas.catalog.dto.ServiceOfferingUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class CatalogController {

	private final ServiceOfferingService serviceOfferingService;

	@GetMapping
	public List<ServiceOfferingResponse> list() {
		return serviceOfferingService.findAll().stream().map(ServiceOfferingResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ServiceOfferingResponse get(@PathVariable UUID id) {
		return ServiceOfferingResponse.from(serviceOfferingService.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ServiceOfferingResponse create(@Valid @RequestBody ServiceOfferingRequest request) {
		return ServiceOfferingResponse.from(serviceOfferingService.create(request.name(), request.description(),
				request.durationMinutes(), request.price(), request.depositAmount()));
	}

	@PutMapping("/{id}")
	public ServiceOfferingResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceOfferingUpdateRequest request) {
		return ServiceOfferingResponse.from(serviceOfferingService.update(id, request.name(), request.description(),
				request.durationMinutes(), request.price(), request.depositAmount(), request.active()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		serviceOfferingService.delete(id);
	}

	@GetMapping("/{id}/professionals")
	public List<UUID> listProfessionals(@PathVariable UUID id) {
		return serviceOfferingService.findProfessionalIdsForService(id);
	}

	@PostMapping("/{id}/professionals")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void assignProfessional(@PathVariable UUID id, @Valid @RequestBody AssignProfessionalRequest request) {
		serviceOfferingService.assignProfessional(id, request.professionalId());
	}

	@DeleteMapping("/{id}/professionals/{professionalId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unassignProfessional(@PathVariable UUID id, @PathVariable UUID professionalId) {
		serviceOfferingService.unassignProfessional(id, professionalId);
	}
}
