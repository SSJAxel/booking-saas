package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.inventory.dto.ProductRequest;
import dev.capibyte.bookingsaas.inventory.dto.ProductResponse;
import dev.capibyte.bookingsaas.inventory.dto.ProductUpdateRequest;
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

/** Managing the product catalog (adding/pricing/removing) is an owner/admin concern, same as ServiceOffering's CatalogController. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ProductController {

	private final ProductService productService;

	@GetMapping
	public List<ProductResponse> list() {
		return productService.findAll().stream().map(ProductResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ProductResponse get(@PathVariable UUID id) {
		return ProductResponse.from(productService.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse create(@Valid @RequestBody ProductRequest request) {
		return ProductResponse.from(productService.create(request.name(), request.price(), request.stock()));
	}

	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
		return ProductResponse.from(
				productService.update(id, request.name(), request.price(), request.stock(), request.active()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		productService.delete(id);
	}
}
