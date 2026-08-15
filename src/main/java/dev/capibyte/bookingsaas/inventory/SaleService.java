package dev.capibyte.bookingsaas.inventory;

import dev.capibyte.bookingsaas.booking.Appointment;
import dev.capibyte.bookingsaas.booking.AppointmentRepository;
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
	private final AppointmentRepository appointmentRepository;

	/** {@code professionalId} attributes the sale for commission purposes (see
	 * ReportService#commissions) — only used when {@code appointmentId} is null. A sale tied to an
	 * appointment always derives the professional from that appointment instead, since the two
	 * can't disagree about who actually did the work. */
	@Transactional
	public Sale recordSale(UUID productId, int quantity, UUID appointmentId, UUID professionalId) {
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
		if (appointmentId != null) {
			Appointment appointment = appointmentRepository.findById(appointmentId)
					.orElseThrow(() -> new NotFoundException("Appointment not found: " + appointmentId));
			sale.setProfessionalId(appointment.getProfessionalId());
		} else {
			sale.setProfessionalId(professionalId);
		}
		return saleRepository.save(sale);
	}

	@Transactional(readOnly = true)
	public Sale findById(UUID id) {
		return saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Sale not found: " + id));
	}
}
