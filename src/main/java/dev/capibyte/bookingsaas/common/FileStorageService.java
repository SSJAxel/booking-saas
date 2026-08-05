package dev.capibyte.bookingsaas.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk file storage — fine for a single-instance MVP (see {@code app.uploads.dir}).
 * Would need to become object storage (S3-like) if this ever runs behind more than one instance.
 */
@Service
public class FileStorageService {

	private final Path root;

	public FileStorageService(@Value("${app.uploads.dir}") String uploadsDir) {
		this.root = Path.of(uploadsDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException ex) {
			throw new UncheckedIOException("Could not create uploads directory: " + root, ex);
		}
	}

	/** Validates the file is a non-empty image, stores it under {@code subdir}, and returns the
	 * path to persist (relative to the uploads root — never store the absolute filesystem path). */
	public String store(MultipartFile file, String subdir) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("An image file is required");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new BadRequestException("Only image files are accepted");
		}
		String extension = extensionOf(file.getOriginalFilename());
		String filename = UUID.randomUUID() + extension;
		String relativePath = subdir + "/" + filename;
		try {
			Path target = root.resolve(relativePath).normalize();
			Files.createDirectories(target.getParent());
			file.transferTo(target);
		} catch (IOException ex) {
			throw new UncheckedIOException("Could not store uploaded file", ex);
		}
		return relativePath;
	}

	public Resource loadAsResource(String relativePath) {
		return new FileSystemResource(resolve(relativePath));
	}

	public Path resolve(String relativePath) {
		return root.resolve(relativePath).normalize();
	}

	private String extensionOf(String originalFilename) {
		if (originalFilename == null) return "";
		int dot = originalFilename.lastIndexOf('.');
		return dot >= 0 ? originalFilename.substring(dot) : "";
	}
}
