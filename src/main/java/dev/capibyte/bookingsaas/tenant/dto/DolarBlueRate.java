package dev.capibyte.bookingsaas.tenant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Only the fields this app reads from DolarAPI's `/v1/dolares/blue` response — also has `casa`,
 * `nombre`, `moneda`, `fechaActualizacion`, ignored here. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DolarBlueRate(BigDecimal compra, BigDecimal venta) {
}
