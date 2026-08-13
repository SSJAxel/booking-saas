package dev.capibyte.bookingsaas.admin.dto;

/** {@code professionalLimitOverride} may be null — that clears the override, falling back to the
 * plan tier's default included count (see Tenant#getEffectiveProfessionalLimit). */
public record UpdateTenantProfessionalLimitRequest(Integer professionalLimitOverride) {
}
