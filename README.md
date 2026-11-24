# MD05 - Session 13: Giám sát hệ thống với Prometheus

Bài tập về nhà Session 13. Mỗi bài có file cấu hình, `README.md` giải thích và trả lời câu bẫy. Output khi chạy thật nằm trong thư mục [`kiem-thu/`](kiem-thu/).

| Bài | Nội dung | Câu bẫy | Thư mục |
|---|---|---|---|
| 1 | Sửa lỗi scrape `State: DOWN, connection refused` | `localhost` trong container là **chính container Prometheus**. Spring Boot dùng **`/actuator/prometheus`**, nên **bắt buộc** khai báo `metrics_path` | [`bai1/`](bai1/) |
| 2 | Spring Boot Actuator (Gradle): mở `prometheus`, đóng `shutdown`/`env` | Thiếu **`io.micrometer:micrometer-registry-prometheus`** thì không có `/actuator/prometheus`, chỉ có JSON | [`bai2/`](bai2/) |
| 3 | Node Exporter theo dõi CPU/RAM/ổ cứng của VPS | Cổng mặc định **9100**, không có mật khẩu/TLS. Public ra Internet là lộ thông tin hệ thống | [`bai3/`](bai3/) |
| 4 | Docker Compose: `ecommerce-api` + `prometheus`, scrape bằng tên service | **Không** tự nhận cấu hình mới. Gọi **`curl -X POST http://localhost:9090/-/reload`** (cần `--web.enable-lifecycle`) | [`bai4/`](bai4/) |

## Cấu trúc

```
bai1/  prometheus.legacy.yml, prometheus.yml (đã sửa), docker-compose.yml
bai2/  Project Spring Boot 3.5 + Gradle (ecommerce-api), application.yml, Dockerfile, test
bai3/  docker-compose.yml (node-exporter + prometheus), prometheus.yml (job 'node')
bai4/  docker-compose.yml (ecommerce-api + prometheus), prometheus.yml
kiem-thu/  output thật của từng bài
```

## Kiểm thử

Chạy thật trên Windows 11, Docker Desktop 29.1 (Compose v5), Prometheus v3.5.0, Node Exporter v1.9.1, JDK 23 (biên dịch `--release 21`).

- **Bài 1**: cấu hình legacy báo **DOWN**, lỗi `connection refused`. Chỉ sửa host thì **DOWN** với lỗi `HTTP 404` (thiếu `metrics_path`). Sửa đủ thì **UP** và đọc được heap, CPU của JVM.
- **Bài 2**: `./gradlew test` **4/4 pass**. `/actuator/prometheus` trả `text/plain;version=0.0.4`. Bản thiếu registry thì endpoint này **404**, `/actuator/metrics` trả JSON. `env`, `shutdown`, `beans`, `heapdump`, `configprops` đều **404**.
- **Bài 3**: job `node` **UP**, đọc được CPU %, RAM, ổ cứng, load. Cổng 9100 **không** truy cập được từ Host, chỉ đi được trong mạng Docker.
- **Bài 4**: 2 target **UP** qua tên service. `ecommerce-api` không lộ cổng ra Host. Sửa file không reload thì Prometheus **vẫn giữ cấu hình cũ**. `POST /-/reload` trả **200**, cấu hình mới có hiệu lực, container không restart. Nạp file lỗi thì nhận **500** và Prometheus giữ cấu hình cũ.
- `promtool check config` cho cả 3 file `prometheus.yml`: **SUCCESS**.

> Máy kiểm thử đã có một Prometheus khác chiếm cổng 9090, nên khi test dùng thêm file [`kiem-thu/docker-compose.port-19190.yml`](kiem-thu/docker-compose.port-19190.yml) để đổi cổng sang 19190:
> `docker compose -f docker-compose.yml -f ../kiem-thu/docker-compose.port-19190.yml up -d`.
> Bình thường chỉ cần `docker compose up -d`.
