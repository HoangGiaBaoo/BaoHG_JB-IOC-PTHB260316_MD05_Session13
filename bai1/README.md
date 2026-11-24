# Bài 1: Khắc phục lỗi Scrape Metrics

| File | Nội dung |
|---|---|
| [`prometheus.legacy.yml`](prometheus.legacy.yml) | Cấu hình gốc bị lỗi (giữ lại để so sánh) |
| [`prometheus.yml`](prometheus.yml) | **Cấu hình đã sửa** |
| [`docker-compose.yml`](docker-compose.yml) | Chạy Prometheus trong Docker, Backend chạy trên Host |

## Cấu hình đã sửa

```yaml
scrape_configs:
  - job_name: 'backend'
    metrics_path: /actuator/prometheus          # sửa 1
    static_configs:
      - targets: ['host.docker.internal:8080']  # sửa 2 (hoặc IP thật của VPS, vd '103.170.120.15:8080')
```

## Câu 1: `localhost` trong file cấu hình trỏ đi đâu?

Mỗi container có network namespace riêng, tức là có card mạng và `localhost` riêng.
Khi Prometheus chạy trong container, `localhost:8080` là **cổng 8080 của chính container Prometheus**, không phải máy Host.
Trong container Prometheus không có gì chạy ở cổng 8080, nên kết nối bị từ chối: `connection refused`.

```
Máy Host
├── Backend Spring Boot :8080      <── host.docker.internal:8080  (ĐÚNG)
└── Container Prometheus
    ├── Prometheus :9090
    └── localhost:8080  ──> không có gì  (SAI: connection refused)
```

## Câu 2: Sửa để Prometheus "nhìn thấy" Backend

Có hai cách:

1. **`host.docker.internal:8080`**: tên DNS đặc biệt trỏ về máy Host.
   - Docker Desktop (Windows/macOS) có sẵn tên này.
   - **Linux/VPS không tự có**. Phải thêm vào container:
     ```yaml
     extra_hosts:
       - "host.docker.internal:host-gateway"
     ```
     (nếu chạy bằng lệnh: `docker run --add-host=host.docker.internal:host-gateway ...`)
2. **IP thật của VPS**, ví dụ `['103.170.120.15:8080']`. Cách này cần Backend lắng nghe trên `0.0.0.0` và firewall cho phép cổng 8080 từ Docker.

## Câu 3 (bẫy): `metrics_path`

- Prometheus mặc định gọi `http://<target>/metrics`.
- Spring Boot **không có** `/metrics`. Endpoint của nó là **`/actuator/prometheus`** (cần Actuator và `micrometer-registry-prometheus`, xem [Bài 2](../bai2/)).
- Vì vậy **bắt buộc** phải thêm `metrics_path: /actuator/prometheus`. Nếu chỉ sửa host mà quên dòng này, target vẫn `DOWN` nhưng với lỗi khác: `server returned HTTP status 404`.

## Kết quả kiểm thử thật ([`../kiem-thu/bai1-targets.txt`](../kiem-thu/bai1-targets.txt))

| Cấu hình | Scrape URL | State | Error |
|---|---|---|---|
| (1) Legacy | `http://localhost:8080/metrics` | **DOWN** | `dial tcp [::1]:8080: connect: connection refused` |
| (2) Chỉ sửa host | `http://host.docker.internal:8080/metrics` | **DOWN** | `server returned HTTP status 404` |
| (3) **Đã sửa** | `http://host.docker.internal:8080/actuator/prometheus` | **UP** | |

Sau khi sửa, PromQL `up{job="backend"}` trả về `1`, đọc được `jvm_memory_used_bytes` và `process_cpu_usage`.

## Cách chạy

```bash
# 1. Chạy backend trên Host (project Bài 2)
cd ../bai2 && ./gradlew bootRun

# 2. Chạy Prometheus trong Docker
cd ../bai1 && docker compose up -d
# Mở http://localhost:9090/targets: job "backend" phải là UP
```
