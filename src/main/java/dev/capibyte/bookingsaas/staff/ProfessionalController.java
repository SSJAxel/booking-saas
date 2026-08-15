package dev.capibyte.bookingsaas.staff;

import dev.capibyte.bookingsaas.common.FileStorageService;
import dev.capibyte.bookingsaas.staff.dto.ProfessionalRequest;
import dev.capibyte.bookingsaas.staff.dto.ProfessionalResponse;
import dev.capibyte.bookingsaas.staff.dto.ProfessionalUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/professionals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ProfessionalController {

	private final ProfessionalService professionalService;
	private final FileStorageService fileStorageService;

	@GetMapping
	public List<ProfessionalResponse> list() {
		return professionalService.findAll().stream().map(ProfessionalResponse::from).toList();
	}

	@GetMapping("/{id}")
	public ProfessionalResponse get(@PathVariable UUID id) {
		return ProfessionalResponse.from(professionalService.findById(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProfessionalResponse create(@Valid @RequestBody ProfessionalRequest request) {
		return ProfessionalResponse.from(professionalService.create(request.branchId(), request.displayName(),
				request.bio(), request.photoUrl(), request.serviceCommissionRate(), request.productCommissionRate()));
	}

	@PutMapping("/{id}")
	public ProfessionalResponse update(@PathVariable UUID id, @Valid @RequestBody ProfessionalUpdateRequest request) {
		return ProfessionalResponse.from(professionalService.update(id, request.branchId(), request.displayName(),
				request.bio(), request.photoUrl(), request.active(), request.serviceCommissionRate(),
				request.productCommissionRate()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id) {
		professionalService.delete(id);
	}

	/** Uploads a photo file for this professional and points photoUrl at it — an alternative to
	 * pasting a URL in PUT /{id}, for owners without the image already hosted somewhere. */
	@PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ProfessionalResponse uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		String relativePath = fileStorageService.store(file, "public/professional-photos");
		return ProfessionalResponse.from(professionalService.updatePhoto(id, "/uploads/" + relativePath));
	}
}
