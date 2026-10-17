package com.jm.inventory.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The only module Spring Boot actually boots. {@code scanBasePackages} widens component
 * scanning beyond this class's own package ({@code com.jm.inventory.api}) to cover the
 * sibling {@code application}/{@code infrastructure} (and, once it exists, {@code domain})
 * modules - otherwise their {@code @Component}/{@code @Service}/{@code @Repository} beans
 * would never be picked up, since Spring Boot's default scan is the main class's package
 * and its sub-packages only.
 */
@SpringBootApplication(scanBasePackages = "com.jm.inventory")
@EntityScan(basePackages = "com.jm.inventory.infrastructure")
@EnableJpaRepositories(basePackages = "com.jm.inventory.infrastructure")
@ConfigurationPropertiesScan(basePackages = "com.jm.inventory")
@EnableCaching
@EnableScheduling
public class InventoryApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApiApplication.class, args);
    }
}
