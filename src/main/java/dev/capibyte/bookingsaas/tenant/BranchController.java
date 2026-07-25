package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.tenant.dto.BranchRequest;
import dev.capibyte.bookingsaas.tenant.dto.BranchResponse;
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

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class BranchController {

	private final BranchService branchService;

	@GetMapping
	public List<BranchResponse> list() {
		return branchService.findAll().stream().map(BranchResponse::from).toList();
	}

	@GetMapping("/{id}")
	public BranchResponse get(@PathVariable UUID id) {
		return BranchResponse.from(branchService.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BranchResponse create(@Valid @RequestBody BranchRequest request) {
		return BranchResponse.from(branchService.create(request.name(), request.address(), request.phone()));
	}

	@PutMapping("/{id}")
	public BranchResponse update(@PathVariable UUID id, @Valid @RequestBody BranchRequest request) {
		return BranchResponse.from(branchService.update(id, request.name(), request.address(), request.phone()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		branchService.delete(id);
	}
}
