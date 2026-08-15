package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.inventory.dto.SaleRequest;
import dev.capibyte.bookingsaas.inventory.dto.SaleResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Recording a sale is day-to-day register work, same as AppointmentController — staff included. */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class SaleController {

	private final SaleService saleService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SaleResponse create(@Valid @RequestBody SaleRequest request) {
		return SaleResponse.from(saleService.recordSale(request.productId(), request.quantity(),
				request.appointmentId(), request.professionalId()));
	}

	@GetMapping("/{id}")
	public SaleResponse get(@PathVariable UUID id) {
		return SaleResponse.from(saleService.findById(id));
	}
}
