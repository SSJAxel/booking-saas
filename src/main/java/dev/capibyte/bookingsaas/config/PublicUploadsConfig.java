package dev.capibyte.bookingsaas.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves tenant-uploaded branding/profile images (logo, banner, professional photos) straight off
 * local disk so the public booking page can render them with a plain {@code <img src>} — no auth
 * needed, they're meant to be public. Deliberately scoped to the {@code public/} subdir of the
 * uploads root (see FileStorageService) — everything else under uploads (e.g. support-report
 * screenshots) stays unreachable by URL, only fetchable through its own authenticated endpoint.
 */
@Configuration
public class PublicUploadsConfig implements WebMvcConfigurer {

	private final String publicUploadsLocation;

	public PublicUploadsConfig(@Value("${app.uploads.dir}") String uploadsDir) {
		Path publicDir = Path.of(uploadsDir).toAbsolutePath().normalize().resolve("public");
		this.publicUploadsLocation = "file:" + publicDir + "/";
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/public/**").addResourceLocations(publicUploadsLocation);
	}
}
