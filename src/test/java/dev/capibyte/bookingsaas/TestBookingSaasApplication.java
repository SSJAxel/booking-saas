package dev.capibyte.bookingsaas;

import org.springframework.boot.SpringApplication;

public class TestBookingSaasApplication {

	public static void main(String[] args) {
		SpringApplication.from(BookingSaasApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
