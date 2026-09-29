# Observability Stack (Docker Compose)

Grafana + OpenTelemetry Collector + Tempo + Loki + Prometheus + Grafana Alloy + Pyroscope + PostgreSQL (+ postgres-exporter)

```
app ──OTLP──► otelcol (4317/4318) ─┬─► tempo      (traces)
          └─► alloy   (14317/14318)├─► loki       (logs)
                                   └─► prometheus (metrics)
alloy ── docker container logs ──► loki
alloy ── pprof (tempo/loki/prometheus/alloy) ──► pyroscope
grafana (postgres DB) ──► prometheus / loki / tempo / pyroscope
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
| Pyroscope | http://localhost:4040 | 프로파일 저장 |
| otelcol OTLP | localhost:4317 (gRPC), 4318 (HTTP) | |
| Alloy OTLP | localhost:14317 (gRPC), 14318 (HTTP) | |
| PostgreSQL | localhost:5432 | grafana / grafana (Grafana DB) |
| postgres-exporter | http://localhost:9187/metrics | `postmaster`, `stat_checkpointer` 수집기 활성화 |

앱에서는 `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` 로 전송하면 됩니다.

## Tempo 테스트용 데모 (`demo/`)

loadgen → nginx (OTel module) ─┬→ java-app (Spring Boot + OpenTelemetry Java agent)
                                 └→ tomcat-app (Tomcat 10.1 WAR + OpenTelemetry Java agent) ─┬→ java-app
                                                                                             └→ PostgreSQL (JDBC)
모든 앱 → otelcol (traces / metrics / logs), Pyroscope (span profiles)

```sh
docker compose up -d            # 메인 스택 먼저
cd demo && docker compose up -d --build
```

| 서비스 | 주소 | 비고 |
|---|---|---|
| nginx | http://localhost:8081 | `nginx:stable-otel`, traceparent 를 java-app 으로 전파 |
| java-app | http://localhost:8082 | `/api/hello`, `/api/slow`, `/api/cpu`, `/api/chain`, `/api/error` (OTel agent + Pyroscope extension) |
| tomcat-app | http://localhost:8083/tomcat-app/ | `hello`, `call` (→ java-app), `db` (JDBC). nginx 경로는 `/tomcat/*`. agent 는 `JAVA_OPTS` 로 지정 |
| loadgen | - | 2초마다 nginx 로 요청 |

`/api/chain` 은 nginx → java-app → (client) → java-app 으로 이어지는 다단 트레이스를 만들고,
`/api/error` 는 1/3 확률로 500 을 반환합니다. java-app 의 로그·메트릭도 OTLP 로 Loki·Prometheus 에 전송됩니다.

## 구성 파일

- `otelcol/config.yaml` — OTLP 수신 → tempo / loki / prometheus
- `alloy/config.alloy` — OTLP 수신 + Docker 컨테이너 로그 수집 + 스택 pprof → Pyroscope
- `tempo/tempo.yaml` — 로컬 스토리지 모노리식 구성
- `prometheus/prometheus.yml` — 스택 구성요소 scrape
- `grafana/datasources.yaml` — Prometheus / Loki / Tempo / Pyroscope 데이터소스 프로비저닝
- `grafana/dashboards.yaml`, `grafana/dashboards/*.json` — 대시보드 프로비저닝 (Grafana `Observability` 폴더)

## 대시보드

| 대시보드 | 출처 | 로컬 수정 사항 |
|---|---|---|
| Grafana Internals | [3590](https://grafana.com/grafana/dashboards/3590) | k8s pod 라벨 → `instance`, 구버전 API 메트릭 → `grafana_http_request_duration_seconds` |
| Prometheus 2.0 Overview | [3662](https://grafana.com/grafana/dashboards/3662) | `http_request_duration_microseconds` → `prometheus_http_request_duration_seconds` |
| Loki Global Metrics | [19772](https://grafana.com/grafana/dashboards/19772) | 데이터소스 연결만 |
| Loki / Container Logs | [13639](https://grafana.com/grafana/dashboards/13639) | `job` → `container` 라벨 (Alloy가 수집한 Docker 로그) |
| PostgreSQL Database | [9628](https://grafana.com/grafana/dashboards/9628) | k8s 라벨(`release`/`namespace`) 제거, PG17 체크포인트 메트릭(`pg_stat_checkpointer_*`) 매칭 |
| OpenTelemetry Collector | [15983](https://grafana.com/grafana/dashboards/15983) | RPC/HTTP 패널이 신규 semconv 메트릭명도 매칭 |
| Tempo Overview | 자체 작성 | Tempo v3 (distributor / live-store / tempodb) |
| Alloy Overview | 자체 작성 | 컴포넌트 컨트롤러, 로그·OTLP 파이프라인, 리소스 |

otelcol 은 `level: detailed` 로 자체 메트릭을 노출하고, Prometheus 는 `metric_name_validation_scheme: legacy` 로
UTF-8 메트릭명(`http.server.request.duration`)을 `_` 로 치환해 수집합니다.

> 비밀번호는 로컬 개발용 기본값입니다. 외부 환경에서는 `.env` 등으로 분리하세요.

## 트레이스 ↔ 로그 ↔ 메트릭 ↔ 프로파일 연동

| 출발 | 도착 | 방법 |
|---|---|---|
| Tempo span | Pyroscope | java-app 의 `pyroscope-otel-javaagent-extension` 이 span 에 `pyroscope.profile.id` 를 붙이고 CPU 프로파일에 span_id 를 기록 → span 상세의 **Profiles for this span** |
| Tempo span | Loki | `{service_name="…"} \| trace_id="…"` (OTLP 로그의 trace_id) |
| Tempo span | Prometheus | Tempo metrics-generator 의 span-metrics (`traces_spanmetrics_*`) |
| Tempo | 서비스 맵 | metrics-generator 의 service-graphs (`traces_service_graph_*`) → Prometheus remote write |
| Loki 로그 | Tempo | derived field `trace_id` |
| Prometheus exemplar | Tempo | `exemplarTraceIdDestinations` (Prometheus `--enable-feature=exemplar-storage`) |

span 프로파일은 CPU(`itimer`) 프로파일에만 연결되며, `PYROSCOPE_FORMAT=jfr` 에서는 연결되지 않습니다.
루트 span 이 nginx 이므로 `OTEL_PYROSCOPE_ROOT_SPAN_ONLY=false` 로 java 의 모든 서버 span 에 프로파일을 연결합니다.

## Grafana 플러그인 / 기능 플래그

- `GF_FEATURE_TOGGLES_ENABLE=grafanaAdvisor` — Administration → Advisor
  (`GF_FEATURE_TOGGLES_GRAFANAADVISOR` 형식은 defaults.ini 에 없는 키라 적용되지 않음)
- `GF_PLUGINS_PREINSTALL=grafana-assistant-app` — Grafana Assistant. self-managed 는 Grafana Cloud 계정 연결 후 사용
