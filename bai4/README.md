# Bài 4: Liên minh Docker Compose (Prometheus + App)

| File | Nội dung |
|---|---|
| [`docker-compose.yml`](docker-compose.yml) | 2 service `ecommerce-api` và `prometheus`, dùng mạng mặc định |
| [`prometheus.yml`](prometheus.yml) | Scrape bằng tên service `ecommerce-api:8080` |
| [`../bai2/Dockerfile`](../bai2/Dockerfile) | Image Spring Boot (multi-stage, chạy bằng user thường) |

## 1. `docker-compose.yml`

```yaml
services:
  ecommerce-api:
    build:
      context: ../bai2
    image: baohg/ecommerce-api:1.0.0
    expose:
      - "8080"             # KHÔNG có "ports": cổng 8080 không lộ ra Host/Internet
    healthcheck: ...       # gọi /actuator/health

  prometheus:
    image: prom/prometheus:v3.5.0
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'      # bật API /-/reload
    ports:
      - "9090:9090"                   # chỉ mở dashboard cho Dev
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro   # nạp file từ Host vào container
      - prometheus-data:/prometheus
    depends_on:
      ecommerce-api:
        condition: service_healthy

volumes:
  prometheus-data:
```

## 2. Docker Network mặc định và scrape bằng tên service

Không khai báo `networks`. Compose tự tạo mạng **`<project>_default`** (ở đây là `ss13-bai4_default`) và gắn cả 2 service vào. DNS nội bộ của Docker phân giải **tên service thành IP container**, nên trong `prometheus.yml` chỉ cần:

```yaml
  - job_name: 'ecommerce-api'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['ecommerce-api:8080']
```

Không cần `host.docker.internal` như Bài 1, cũng không cần biết IP container (IP đổi mỗi lần tạo lại container).

## 3. Lệnh nạp `prometheus.yml` từ Host vào container

Dùng **bind mount** trong `docker-compose.yml`:

```yaml
volumes:
  - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro
```

Lệnh tương đương khi chạy riêng bằng `docker run`:

```bash
docker run -d --name prometheus -p 9090:9090 \
  -v "$(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus:v3.5.0 --config.file=/etc/prometheus/prometheus.yml --web.enable-lifecycle
```

Mount `:ro` để Prometheus không ghi ngược lên file của Host.

## 4. Bẫy: sửa `prometheus.yml` trên Host, Prometheus có tự nhận không?

**Không.** File trong container thay đổi ngay (vì là bind mount), nhưng Prometheus chỉ đọc cấu hình **một lần lúc khởi động** rồi giữ trong RAM. Phải ra lệnh nạp lại:

```bash
# 0. Kiểm tra cú pháp trước (nếu sai, reload sẽ bị từ chối)
docker compose exec prometheus promtool check config /etc/prometheus/prometheus.yml

# Cách 1 (khuyên dùng): gọi API HTTP POST. Cần cờ --web.enable-lifecycle
curl -X POST http://localhost:9090/-/reload

# Cách 2: gửi tín hiệu SIGHUP (không cần cờ lifecycle)
docker compose kill -s SIGHUP prometheus

# Cách 3: restart container. Được nhưng dashboard bị gián đoạn vài giây
docker compose restart prometheus
```

Reload (cách 1, 2) không tắt tiến trình nên không mất dữ liệu đang scrape. Cách 3 thì có.

Kết quả kiểm thử thật ([`../kiem-thu/bai4-reload.txt`](../kiem-thu/bai4-reload.txt)):

```
Trước khi sửa: scrape_interval = 15s
Sửa trên Host thành 5s; trong container file cũng đã là 5s
Sau 20 giây KHÔNG reload: Prometheus vẫn dùng 15s    <-- không tự nhận
curl -X POST http://localhost:9090/-/reload  -> HTTP 200
Sau reload: scrape_interval = 5s; container KHÔNG restart (RestartCount=0)
SIGHUP: nạp lại thành công
Nạp file LỖI cú pháp -> HTTP 500, Prometheus vẫn chạy với cấu hình cũ
```

> **Lưu ý khi mount 1 file đơn lẻ:** Docker gắn theo *inode*. Một số trình soạn thảo (vim, `sed -i`...) lưu bằng cách tạo file mới rồi đổi tên, làm inode thay đổi, nên container vẫn thấy **file cũ**. Khi đó reload vô dụng và phải `docker compose up -d --force-recreate prometheus`. Muốn tránh hẳn, mount cả thư mục: `./prometheus:/etc/prometheus:ro`.

## Kết quả kiểm thử thật ([`../kiem-thu/bai4.txt`](../kiem-thu/bai4.txt))

```
SERVICE         STATUS                    PORTS
ecommerce-api   Up (healthy)              8080/tcp            <- không publish ra Host
prometheus      Up                        0.0.0.0:9090->9090/tcp

job=ecommerce-api url=http://ecommerce-api:8080/actuator/prometheus health=up
job=prometheus    url=http://localhost:9090/metrics                health=up

Host: curl localhost:8080             -> không kết nối được
Container prometheus -> ecommerce-api:8080/actuator/health -> {"status":"UP"}
```

## Cách chạy

```bash
docker compose up -d --build
# Dashboard: http://localhost:9090/targets
docker compose down        # dừng (thêm -v để xóa luôn dữ liệu metric)
```
