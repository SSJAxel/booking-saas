package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.common.NotFoundException;
import dev.capibyte.bookingsaas.common.TenantContext;
import dev.capibyte.bookingsaas.tenant.PlanTier;
import dev.capibyte.bookingsaas.tenant.TenantService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
	private final TenantService tenantService;

	@Transactional
	public Product create(String name, BigDecimal price, int stock) {
		PlanTier tier = tenantService.findById(TenantContext.getTenantId()).getPlanTier();
		Integer maxProducts = tier.getMaxProducts();
		if (maxProducts != null && productRepository.countByActiveTrue() >= maxProducts) {
			throw new PlanLimitExceededException(tier);
		}

		Product product = new Product();
		product.setName(name);
		product.setPrice(price);
		product.setStock(stock);
		return productRepository.save(product);
	}

	@Transactional(readOnly = true)
	public List<Product> findAll() {
		return productRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Product findById(UUID id) {
		return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found: " + id));
	}

	@Transactional
	public Product update(UUID id, String name, BigDecimal price, int stock, boolean active) {
		Product product = findById(id);
		product.setName(name);
		product.setPrice(price);
		product.setStock(stock);
		product.setActive(active);
		return product;
	}

	@Transactional
	public void delete(UUID id) {
		if (!productRepository.existsById(id)) {
			throw new NotFoundException("Product not found: " + id);
		}
		productRepository.deleteById(id);
	}
}
