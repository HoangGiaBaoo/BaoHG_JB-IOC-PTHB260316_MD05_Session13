package com.baohg.ecommerce.product;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * API demo của E-commerce Backend. Mọi request vào đây được Micrometer
 * tự động đo (metric http_server_requests_seconds_*).
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final List<Product> PRODUCTS = List.of(
            new Product(1L, "iPhone 16", new BigDecimal("22990000"), 15),
            new Product(2L, "Samsung Galaxy S25", new BigDecimal("19990000"), 8),
            new Product(3L, "Xiaomi 15", new BigDecimal("12490000"), 30)
    );

    private final Counter productViews;

    public ProductController(MeterRegistry registry) {
        // Metric nghiệp vụ tự định nghĩa, trên Prometheus có tên: ecommerce_product_views_total
        this.productViews = Counter.builder("ecommerce.product.views")
                .description("Số lần xem chi tiết sản phẩm")
                .register(registry);
    }

    @GetMapping
    public List<Product> findAll() {
        return PRODUCTS;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> findById(@PathVariable Long id) {
        productViews.increment();
        return PRODUCTS.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
