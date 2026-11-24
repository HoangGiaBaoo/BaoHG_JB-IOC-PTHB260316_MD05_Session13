package com.baohg.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng cấu hình Bài 2:
 * - /actuator/prometheus trả về TEXT chuẩn Prometheus (không phải JSON)
 * - các endpoint nguy hiểm bị đóng
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void prometheusEndpointTraVeDinhDangText() throws Exception {
        mvc.perform(get("/api/products/1")).andExpect(status().isOk());

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/plain")))
                .andExpect(content().string(containsString("# TYPE jvm_memory_used_bytes gauge")))
                .andExpect(content().string(containsString("process_cpu_usage")))
                .andExpect(content().string(containsString("ecommerce_product_views_total")))
                .andExpect(content().string(containsString("application=\"ecommerce-api\"")));
    }

    @Test
    void healthVanMo() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void shutdownBiDong() throws Exception {
        mvc.perform(post("/actuator/shutdown")).andExpect(status().isNotFound());
    }

    @Test
    void cacEndpointNguyHiemBiDong() throws Exception {
        String[] endpoints = {"env", "beans", "configprops", "heapdump", "threaddump", "loggers", "mappings", "metrics"};
        for (String ep : endpoints) {
            mvc.perform(get("/actuator/" + ep)).andExpect(status().isNotFound());
        }
    }
}
