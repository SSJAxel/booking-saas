// Espejo de PlanTier.java — mantener sincronizado a mano si cambia la matriz de planes.
export const PLAN_LIMITS = {
	TRIAL: {
		maxProfessionals: 4,
		maxProducts: 5,
		maxBranches: 2,
		maxServices: 6,
		maxAppointmentsPerWeek: null,
		mercadoPagoEnabled: false,
		whatsappEnabled: true,
		loyaltyRewardsEnabled: false,
		commissionsEnabled: false,
	},
	PERSONAL: {
		maxProfessionals: 1,
		maxProducts: 0,
		maxBranches: 1,
		maxServices: 3,
		maxAppointmentsPerWeek: 20,
		mercadoPagoEnabled: false,
		whatsappEnabled: false,
		loyaltyRewardsEnabled: false,
		commissionsEnabled: false,
	},
	BASIC: {
		maxProfessionals: 4,
		maxProducts: 5,
		maxBranches: 2,
		maxServices: 6,
		maxAppointmentsPerWeek: null,
		mercadoPagoEnabled: false,
		whatsappEnabled: true,
		loyaltyRewardsEnabled: false,
		commissionsEnabled: false,
	},
	PRO: {
		maxProfessionals: 10,
		maxProducts: 10,
		maxBranches: 4,
		maxServices: 8,
		maxAppointmentsPerWeek: null,
		mercadoPagoEnabled: true,
		whatsappEnabled: true,
		loyaltyRewardsEnabled: true,
		commissionsEnabled: true,
	},
	MAX: {
		maxProfessionals: 20,
		maxProducts: 20,
		maxBranches: 8,
		maxServices: 12,
		maxAppointmentsPerWeek: null,
		mercadoPagoEnabled: true,
		whatsappEnabled: true,
		loyaltyRewardsEnabled: true,
		commissionsEnabled: true,
	},
};

export function planHasWhatsApp(tier) {
	return PLAN_LIMITS[tier]?.whatsappEnabled ?? true;
}

export function planHasLoyaltyRewards(tier) {
	return PLAN_LIMITS[tier]?.loyaltyRewardsEnabled ?? false;
}

export function planHasCommissions(tier) {
	return PLAN_LIMITS[tier]?.commissionsEnabled ?? false;
}

export function planHasProducts(tier) {
	return (PLAN_LIMITS[tier]?.maxProducts ?? 1) !== 0;
}
