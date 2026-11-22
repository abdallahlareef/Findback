package edu.findback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"edu.findback", "com.findback.module3"})
@EntityScan(basePackages = {"edu.findback", "com.findback.module3.domain"})
@EnableJpaRepositories(basePackages = {"edu.findback", "com.findback.module3.repository"})
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
