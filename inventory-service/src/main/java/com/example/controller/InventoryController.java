package com.example.controller;

import com.example.model.Product;
import com.example.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }


    // POST /inventory/reserve - order-service sends orderId and orderState so we
    // can check the protocol before reserving anything
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserveStock(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String orderState = (String) request.get("orderState");
        String productId = (String) request.get("productId");
        int quantity = (Integer) request.get("quantity");

        String result = inventoryService.reserveStock(orderId, orderState, productId, quantity);

        // the service returns a status string; split it into the two flags order-service reads
        boolean reserved = result.equals("OK");
        boolean protocolViolation = result.startsWith("PROTOCOL_VIOLATION");

        Map<String, Object> response = new HashMap<>();
        response.put("reserved", reserved);
        response.put("protocolViolation", protocolViolation);
        response.put("message", reserved ? "Stock reserved successfully" : result);
        return ResponseEntity.ok(response);
    }

    // POST /inventory/release - puts reserved stock back
    @PostMapping("/release")
    public ResponseEntity<Map<String, Object>> releaseStock(@RequestBody Map<String, Object> request) {
        String productId = (String) request.get("productId");
        int quantity = (Integer) request.get("quantity");

        boolean released = inventoryService.releaseStock(productId, quantity);

        Map<String, Object> response = new HashMap<>();
        response.put("released", released);
        response.put("message", released ? "Stock released successfully" : "Product not found");
        return ResponseEntity.ok(response);
    }

    // GET /inventory/products - used to check stock before and after a demo run
    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(inventoryService.getAllProducts());
    }

    // GET /inventory/products/{productId} - 404 if not stocked
    @GetMapping("/products/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable String productId) {
        return inventoryService.getProduct(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /inventory/products - seeds a product so orders have stock to reserve
    @PostMapping("/products")
    public ResponseEntity<Product> addProduct(@RequestBody Product product) {
        return ResponseEntity.ok(inventoryService.addProduct(product));
    }
}
