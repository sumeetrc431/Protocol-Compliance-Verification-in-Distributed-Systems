package com.example;

import com.example.model.Product;
import com.example.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
public class InventoryServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedData(InventoryService inventoryService) {
        return args -> {
            if (inventoryService.getAllProducts().isEmpty()) {
                log.info("Seeding initial product inventory...");

                Product p1 = new Product();
                p1.setProductId("PROD-001");
                p1.setName("Laptop Pro 15");
                p1.setTotalStock(50);
                p1.setUnitPrice(new BigDecimal("999.99"));
                inventoryService.addProduct(p1);

                Product p2 = new Product();
                p2.setProductId("PROD-002");
                p2.setName("Wireless Keyboard");
                p2.setTotalStock(100);
                p2.setUnitPrice(new BigDecimal("49.99"));
                inventoryService.addProduct(p2);

                Product p3 = new Product();
                p3.setProductId("PROD-003");
                p3.setName("USB-C Hub");
                p3.setTotalStock(200);
                p3.setUnitPrice(new BigDecimal("29.99"));
                inventoryService.addProduct(p3);

                log.info("Seeded 3 products into inventory");
            }
        };
    }
}
