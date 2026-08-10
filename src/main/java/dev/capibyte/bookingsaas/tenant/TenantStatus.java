package dev.capibyte.bookingsaas.tenant;

public enum TenantStatus {
	/** Newly registered, not yet reviewed by the founder — PublicTenantResolutionFilter blocks the
	 * public booking site for any status other than ACTIVE. The owner's own panel (login,
	 * configuring branches/professionals/services) stays open the whole time: the founder needs to
	 * see what was set up (how many professionals, in particular — pricing is per-employee) before
	 * approving. See PlatformAdminService#approveTenant. */
	PENDING_APPROVAL,
	ACTIVE,
	SUSPENDED
}
