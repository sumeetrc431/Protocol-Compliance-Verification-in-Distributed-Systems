package com.example.service;

import com.example.model.Product;
import com.example.protocol.InventoryProtocolCheck;
import com.example.protocol.ReservedOrdersTracker;
import com.example.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final InventoryProtocolCheck protocolCheck;
    private final ReservedOrdersTracker reservedOrdersTracker;

    @Autowired
    public InventoryService(ProductRepository productRepository,
                             InventoryProtocolCheck protocolCheck,
                             ReservedOrdersTracker reservedOrdersTracker) {
        this.productRepository = productRepository;
        this.protocolCheck = protocolCheck;
        this.reservedOrdersTracker = reservedOrdersTracker;
    }

    // Reserves stock for an order. Takes orderId and orderState so the protocol
    // and duplicate checks can run before any stock is touched.
    @Transactional
    public String reserveStock(Long orderId, String orderState, String productId, int quantity) {

        // protocol check first - is order service even allowed to ask for this right now?
        if (!protocolCheck.isReserveAllowed(orderState)) {
            log.info("Protocol violation - reserve called with orderId={} but order state was '{}', expected '{}'",
                    orderId, orderState, protocolCheck.getExpectedState());
            return "PROTOCOL_VIOLATION: expected order state '" + protocolCheck.getExpectedState()
                    + "' but got '" + orderState + "'";
        }

        // duplicate check - has this order already reserved stock before?
        if (reservedOrdersTracker.hasAlreadyReserved(orderId)) {
            log.info("Protocol violation - orderId={} already reserved stock once, rejecting duplicate reserve", orderId);
            return "PROTOCOL_VIOLATION: orderId " + orderId + " already reserved stock";
        }

        // stock checks only after both protocol checks have passed
        Optional<Product> productOpt = productRepository.findByProductId(productId);

        if (productOpt.isEmpty()) {
            log.info("Reserve failed - product not found: productId={}", productId);
            return "FAILED: product not found";
        }

        Product product = productOpt.get();
        int available = product.getAvailableStock();

        if (available < quantity) {
            log.info("Reserve failed - insufficient stock: productId={} requested={} available={}",
                    productId, quantity, available);
            return "FAILED: insufficient stock";
        }

        // reserved rather than deducted, so available stock is total minus reserved
        product.setReservedStock(product.getReservedStock() + quantity);
        productRepository.save(product);
        reservedOrdersTracker.markAsReserved(orderId);

        log.info("Stock reserved: orderId={} productId={} quantity={} remainingAvailable={}",
                orderId, productId, quantity, product.getAvailableStock());
        return "OK";
    }

    // undoes a reservation; not part of the order lifecycle protocol
    @Transactional
    public boolean releaseStock(String productId, int quantity) {
        Optional<Product> productOpt = productRepository.findByProductId(productId);

        if (productOpt.isEmpty()) {
            log.info("Release failed - product not found: productId={}", productId);
            return false;
        }

        Product product = productOpt.get();
        int newReserved = Math.max(0, product.getReservedStock() - quantity);
        product.setReservedStock(newReserved);
        productRepository.save(product);
        log.info("Stock released: productId={} quantity={}", productId, quantity);
        return true;
    }

    public Optional<Product> getProduct(String productId) {
        return productRepository.findByProductId(productId);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // used to seed products before a demo run
    @Transactional
    public Product addProduct(Product product) {
        log.info("Adding product: productId={} name={} stock={}",
                product.getProductId(), product.getName(), product.getTotalStock());
        return productRepository.save(product);
    }
}
