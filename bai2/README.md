# Bài 2: Bật Radar cho Spring Boot (Actuator)

Project Spring Boot 3.5 (Gradle) mô phỏng E-commerce Backend: có API `/api/products` và mở endpoint `/actuator/prometheus` cho Prometheus.

| File | Nội dung |
|---|---|
| [`build.gradle`](build.gradle) | Thêm `spring-boot-starter-actuator` và **`micrometer-registry-prometheus`** |
| [`src/main/resources/application.yml`](src/main/resources/application.yml) | Mở `prometheus`, đóng `shutdown`, `env`... |
| [`ActuatorSecurityTest.java`](src/test/java/com/baohg/ecommerce/ActuatorSecurityTest.java) | Test tự động kiểm chứng cấu hình |
| [`Dockerfile`](Dockerfile) | Build image cho [Bài 4](../bai4/) |

## 1. Dependency trong `build.gradle`

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Actuator: sinh ra /actuator/**, kèm micrometer-core để đo CPU, Heap, GC, HTTP...
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Dependency hay bị quên: xuất metric theo định dạng text của Prometheus
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

Không cần ghi version vì plugin `io.spring.dependency-management` lấy version đúng từ Spring Boot BOM.

## 2. `application.yml`: mở Prometheus, khóa endpoint nguy hiểm

```yaml
management:
  endpoints:
    access:
      default: none                        # deny-by-default: tắt hết mọi endpoint
    web:
      exposure:
        include: health,info,prometheus    # chỉ phơi bày 3 endpoint này qua HTTP
        exclude: shutdown,env,configprops,beans,heapdump,threaddump,loggers,mappings,...
  endpoint:
    health:     { access: read-only, show-details: never }
    info:       { access: read-only }
    prometheus: { access: read-only }
    shutdown:   { access: none }           # POST /actuator/shutdown: tắt app từ xa
    env:        { access: none, show-values: never }   # lộ mật khẩu DB, API key
    heapdump:   { access: none }           # tải toàn bộ RAM của JVM
  metrics:
    tags:
      application: ${spring.application.name}   # gắn label application="ecommerce-api"
```

Cấu hình dùng hai lớp bảo vệ:
1. **`access`** quyết định endpoint có được *bật* hay không. `default: none` tắt hết, sau đó chỉ bật lại 3 endpoint cần dùng.
   (Spring Boot < 3.4 dùng `management.endpoints.enabled-by-default: false` và `management.endpoint.<id>.enabled: true`.)
2. **`exposure`** quyết định endpoint đã bật có được *phơi bày qua HTTP* hay không. `exclude` luôn được ưu tiên hơn `include`, nên dù ai đó sửa thành `include: "*"` thì `env` và `shutdown` vẫn bị chặn.

> Môi trường production nên chạy thêm management port riêng (`management.server.port: 8081`) và chỉ mở port đó trong mạng nội bộ, hoặc bảo vệ `/actuator/**` bằng Spring Security.

## 3. Bẫy: thiếu `micrometer-registry-prometheus`

`spring-boot-starter-actuator` chỉ mang theo **`micrometer-core`**, chưa có "registry" nào để xuất dữ liệu. Nếu thiếu dependency **`io.micrometer:micrometer-registry-prometheus`**:
- **Không có** endpoint `/actuator/prometheus` (404), Prometheus không scrape được.
- Chỉ có `/actuator/metrics/...`, trả về **JSON** (`application/vnd.spring-boot.actuator.v3+json`). Prometheus không đọc được JSON.

Đã kiểm thử thật cả hai trường hợp ([`../kiem-thu/bai2.txt`](../kiem-thu/bai2.txt)):

| | Thiếu registry | Đủ registry |
|---|---|---|
| `GET /actuator/prometheus` | **404** | **200** `text/plain;version=0.0.4` |
| Định dạng | `{"name":"jvm.memory.used","measurements":[...]}` (JSON) | `jvm_memory_used_bytes{area="heap",...} 2.09E7` (text) |

Endpoint nguy hiểm sau khi cấu hình:

```
GET  /actuator/env         -> 404
POST /actuator/shutdown    -> 404
GET  /actuator/beans       -> 404
GET  /actuator/heapdump    -> 404
GET  /actuator/configprops -> 404
GET  /actuator             -> chỉ liệt kê health, info, prometheus
```

## Chạy và kiểm thử

```bash
./gradlew test          # 4 test: prometheus là text, health UP, shutdown/env... bị đóng
./gradlew bootRun       # http://localhost:8080/actuator/prometheus
```

Kết quả: `BUILD SUCCESSFUL`, 4/4 test pass.

Metric nghiệp vụ tự định nghĩa: mỗi lần gọi `GET /api/products/{id}`, counter `ecommerce_product_views_total` tăng thêm 1.
