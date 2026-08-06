package dev.capibyte.bookingsaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class BookingSaasApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingSaasApplication.class, args);
	}

	/** Small dedicated pool for one-off delayed tasks (see WhatsAppNotificationService's
	 * per-recipient send throttle) — separate from the implicit scheduler @Scheduled methods use,
	 * so a burst of delayed WhatsApp sends can never starve the deposit-expiration poll. */
	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("delayed-task-");
		scheduler.initialize();
		return scheduler;
	}

}
