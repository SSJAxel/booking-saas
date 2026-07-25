package dev.capibyte.bookingsaas.tenant;

import dev.capibyte.bookingsaas.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository branchRepository;

	@Transactional
	public Branch create(String name, String address, String phone) {
		Branch branch = new Branch();
		branch.setName(name);
		branch.setAddress(address);
		branch.setPhone(phone);
		return branchRepository.save(branch);
	}

	@Transactional(readOnly = true)
	public List<Branch> findAll() {
		return branchRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Branch findById(UUID id) {
		return branchRepository.findById(id).orElseThrow(() -> new NotFoundException("Branch not found: " + id));
	}

	@Transactional
	public Branch update(UUID id, String name, String address, String phone) {
		Branch branch = findById(id);
		branch.setName(name);
		branch.setAddress(address);
		branch.setPhone(phone);
		return branch;
	}

	@Transactional
	public void delete(UUID id) {
		if (!branchRepository.existsById(id)) {
			throw new NotFoundException("Branch not found: " + id);
		}
		branchRepository.deleteById(id);
	}
}
