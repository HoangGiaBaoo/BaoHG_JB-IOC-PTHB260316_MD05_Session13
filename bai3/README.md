# Bài 3: Khám sức khỏe máy chủ với Node Exporter

Actuator chỉ biết về **JVM** (heap, GC, thread của app Java). Nó không biết ổ cứng VPS sắp đầy hay RAM hệ điều hành bị tiến trình khác chiếm hết. **Node Exporter** đọc các thông số đó từ kernel Linux.

| File | Nội dung |
|---|---|
| [`docker-compose.yml`](docker-compose.yml) | Node Exporter (mount `/proc`, `/sys`, `/`) và Prometheus |
| [`prometheus.yml`](prometheus.yml) | Thêm `job_name: 'node'` |

## 1. `docker-compose.yml`

```yaml
services:
  node-exporter:
    image: prom/node-exporter:v1.9.1
    pid: host
    command:
      - '--path.procfs=/host/proc'
      - '--path.sysfs=/host/sys'
      - '--path.rootfs=/rootfs'
      - '--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)'
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    expose:
      - "9100"          # chỉ mở trong mạng Docker, KHÔNG publish ra Internet
```

## 2. Các volume đặc biệt cần mount

Container có `/proc` và `/sys` **riêng, ảo**. Nếu không mount, Node Exporter chỉ thấy tiến trình của chính nó. Ta mount thư mục của Host vào một đường dẫn khác (`/host/...`), rồi dùng `--path.*` để chỉ cho Node Exporter đọc ở đó:

| Volume Host | Mount vào | Dùng để đo | Cờ |
|---|---|---|---|
| **`/proc`** | `/host/proc:ro` | CPU, RAM, load average, tiến trình, network, disk I/O | `--path.procfs` |
| **`/sys`** | `/host/sys:ro` | Phần cứng: ổ đĩa, card mạng, nhiệt độ, tần số CPU | `--path.sysfs` |
| **`/`** | `/rootfs:ro` | Dung lượng ổ cứng (đã dùng / còn trống) | `--path.rootfs` |

- Tất cả đều mount **`:ro`** (chỉ đọc), container không thể sửa gì trên Host.
- `pid: host`: dùng chung PID namespace với Host, để thấy tiến trình thật.
  Kiểm thử: có `pid: host` thấy **32** tiến trình, container thường chỉ thấy **2**.

## 3. Bổ sung `prometheus.yml`

```yaml
scrape_configs:
  - job_name: 'backend'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.docker.internal:8080']

  - job_name: 'node'
    # Node Exporter dùng đúng /metrics (mặc định) nên KHÔNG cần metrics_path
    static_configs:
      - targets: ['node-exporter:9100']   # tên service trong cùng mạng Docker
```

Nếu Node Exporter chạy ở máy khác (hoặc cài thẳng bằng systemd), dùng `['<IP-VPS>:9100']`.

## 4. Bẫy: cổng 9100 và rủi ro khi public

- Node Exporter mặc định chạy ở **cổng `9100`**.
- Mặc định **không có xác thực, không có TLS**. Nếu public `9100` ra Internet, bất kỳ ai cũng đọc được:
  - hostname, phiên bản kernel, OS (`node_uname_info`), giúp kẻ tấn công tìm lỗ hổng theo đúng phiên bản;
  - danh sách ổ đĩa, mount point, card mạng, địa chỉ;
  - mức tải CPU/RAM theo thời gian thực, dùng để canh lúc server yếu nhất mà tấn công DoS;
  - mỗi request `/metrics` tốn CPU, nên spam request cũng là một kiểu DoS nhẹ.
  Các bot quét Internet (Shodan...) tìm cổng 9100 mở rất thường xuyên.
- Cách phòng:
  1. **Không publish cổng** (`expose` thay vì `ports`). Prometheus gọi qua mạng nội bộ Docker. Đây là cách dùng trong bài.
  2. Cần truy cập từ Host thì chỉ bind loopback: `ports: ["127.0.0.1:9100:9100"]`.
  3. Firewall: `ufw allow from <IP-Prometheus> to any port 9100` và chặn các IP khác.
     Lưu ý: cổng Docker publish **đi vòng qua UFW** (Docker tự ghi iptables), nên cách 1 và 2 an toàn hơn.
  4. Bắt buộc phải mở ra ngoài thì bật Basic Auth và TLS bằng `--web.config.file=/etc/node-exporter/web.yml`:
     ```yaml
     # web.yml
     tls_server_config:
       cert_file: node.crt
       key_file: node.key
     basic_auth_users:
       prometheus: $2y$10$...   # mật khẩu đã hash bcrypt (htpasswd -nBC 10 "" | tr -d ':\n')
     ```
     và khai báo `basic_auth` / `scheme: https` trong job `node` của `prometheus.yml`.

## Kết quả kiểm thử thật ([`../kiem-thu/bai3.txt`](../kiem-thu/bai3.txt))

```
job=backend url=http://host.docker.internal:8080/actuator/prometheus health=up
job=node    url=http://node-exporter:9100/metrics               health=up
CPU đang dùng            : 31.9 %
RAM còn khả dụng         : 72.4 %   (tổng 3.82 GB)
Ổ cứng đã dùng           : 7.7 %
Load average 1 phút      : 0.3
Từ Host: curl localhost:9100        -> không kết nối được (cổng không lộ ra ngoài)
Từ container Prometheus  -> 1824 dòng metric node_*
```

> Kiểm thử trên Docker Desktop (Windows). Ở đây `/` của VM là overlay nên bị loại, ổ thật nằm ở mount point `/var/lib`.
> Trên VPS Ubuntu thật, ổ gốc là ext4 và truy vấn dùng `mountpoint="/"`:
> `100 - node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"} * 100`.
> Dòng log `Failed to open directory /run/udev/data` trên Docker Desktop là bình thường; trên VPS có thể mount thêm `/run/udev/data:/run/udev/data:ro`.

## Cách chạy

```bash
docker compose up -d
# http://localhost:9090/targets: job "node" UP
```
