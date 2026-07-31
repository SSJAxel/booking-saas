package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.common.BadRequestException;
import dev.capibyte.bookingsaas.common.NotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleService {

	private final SaleRepository saleRepository;
	private final ProductRepository productRepository;
	private final ProductService productService;

	@Transactional
	public Sale recordSale(UUID productId, int quantity, UUID appointmentId) {
		Product product = productService.findById(productId);
		if (!product.isActive()) {
			throw new BadRequestException("Product is not active: " + productId);
		}

		int updated = productRepository.decrementStockIfAvailable(productId, quantity);
		if (updated == 0) {
			throw new InsufficientStockException();
		}

		Sale sale = new Sale();
		sale.setProductId(productId);
		sale.setAppointmentId(appointmentId);
		sale.setQuantity(quantity);
		sale.setAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
		return saleRepository.save(sale);
	}

	@Transactional(readOnly = true)
	public Sale findById(UUID id) {
		return saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Sale not found: " + id));
	}
}
