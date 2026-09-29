# Observability Stack (Docker Compose)

Grafana + OpenTelemetry Collector + Tempo + Loki + Prometheus + Grafana Alloy + PostgreSQL

```
app ──OTLP──► otelcol (4317/4318) ─┬─► tempo      (traces)
          └─► alloy   (14317/14318)├─► loki       (logs)
                                   └─► prometheus (metrics)
alloy ── docker container logs ──► loki
grafana (postgres DB) ──► prometheus / loki / tempo
```

## 사전 준비

Grafana 플러그인 저장용 외부 볼륨을 한 번 만들어 둡니다.

```sh
docker volume create grafana-storage
```

## 실행

```sh
docker compose up -d
docker compose ps
docker compose down   # 중지 (데이터 볼륨 유지)
```

## 접속 정보

| 서비스 | 주소 | 비고 |
|---|---|---|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | OTLP·remote-write 수신 활성화 |
| Loki | http://localhost:3100 | |
| Tempo | http://localhost:3200 | |
| Alloy UI | http://localhost:12345 | |
| otelcol OTLP | localhost:4317 (gRPC), 4318 (HTTP) | |
| Alloy OTLP | localhost:14317 (gRPC), 14318 (HTTP) | |
| PostgreSQL | localhost:5432 | grafana / grafana (Grafana DB) |

앱에서는 `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` 로 전송하면 됩니다.

## 구성 파일

- `otelcol/config.yaml` — OTLP 수신 → tempo / loki / prometheus
- `alloy/config.alloy` — OTLP 수신 + Docker 컨테이너 로그 수집
- `tempo/tempo.yaml` — 로컬 스토리지 모노리식 구성
- `prometheus/prometheus.yml` — 스택 구성요소 scrape
- `grafana/datasources.yaml` — Prometheus / Loki / Tempo 데이터소스 프로비저닝

> 비밀번호는 로컬 개발용 기본값입니다. 외부 환경에서는 `.env` 등으로 분리하세요.
