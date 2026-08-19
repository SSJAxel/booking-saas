package dev.capibyte.bookingsaas.catalog;

import dev.capibyte.bookingsaas.catalog.dto.ServiceComboRequest;
import dev.capibyte.bookingsaas.catalog.dto.ServiceComboResponse;
import dev.capibyte.bookingsaas.catalog.dto.ServiceComboUpdateRequest;
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
@RequestMapping("/api/service-combos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ServiceComboController {

	private final ServiceComboService serviceComboService;

	@GetMapping
	public List<ServiceComboResponse> list() {
		return serviceComboService.findAll().stream().map(ServiceComboResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ServiceComboResponse create(@Valid @RequestBody ServiceComboRequest request) {
		return ServiceComboResponse.from(serviceComboService.create(request.serviceAId(), request.serviceBId(),
				request.comboPrice(), request.comboDepositAmount()));
	}

	@PutMapping("/{id}")
	public ServiceComboResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceComboUpdateRequest request) {
		return ServiceComboResponse.from(
				serviceComboService.update(id, request.comboPrice(), request.comboDepositAmount(), request.active()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		serviceComboService.delete(id);
	}
}
