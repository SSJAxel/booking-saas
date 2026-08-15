package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.tenant.dto.RewardTierRequest;
import dev.capibyte.bookingsaas.tenant.dto.RewardTierResponse;
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

/** Configuring the loyalty program's reward tiers — readable by STAFF (they need to know what's
 * redeemable to serve a walk-in), writable by OWNER/ADMIN only (defining the actual rewards is a
 * business decision, same split as ClientController's search-vs-pin/notes). */
@RestController
@RequestMapping("/api/tenant/loyalty-rewards/tiers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
public class RewardTierController {

	private final RewardTierService rewardTierService;

	@GetMapping
	public List<RewardTierResponse> list() {
		return rewardTierService.findAll().stream().map(RewardTierResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public RewardTierResponse create(@Valid @RequestBody RewardTierRequest request) {
		return RewardTierResponse.from(rewardTierService.create(request.pointsRequired(), request.description()));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public RewardTierResponse update(@PathVariable UUID id, @Valid @RequestBody RewardTierRequest request) {
		return RewardTierResponse.from(rewardTierService.update(id, request.pointsRequired(), request.description()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
	public void delete(@PathVariable UUID id) {
		rewardTierService.delete(id);
	}
}
