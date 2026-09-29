package demo;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
@RestController
public class DemoApplication {

    private static final Logger log = LoggerFactory.getLogger(DemoApplication.class);

    // 자기 자신을 호출해 client span → server span 으로 이어지는 하위 트레이스를 만든다
    private final RestClient self = RestClient.create("http://localhost:8080");

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping("/api/hello")
    public Map<String, String> hello() {
        log.info("hello called");
        return Map.of("message", "hello from java-app");
    }

    @GetMapping("/api/slow")
    public Map<String, Object> slow() throws InterruptedException {
        long ms = ThreadLocalRandom.current().nextLong(50, 500);
        Thread.sleep(ms);
        log.info("slow call took {} ms", ms);
        return Map.of("sleptMs", ms);
    }

    // CPU 를 쓰는 구간: span profile(Tempo → Pyroscope) 확인용
    @GetMapping("/api/cpu")
    public Map<String, Object> cpu() {
        int limit = ThreadLocalRandom.current().nextInt(200_000, 600_000);
        int primes = countPrimes(limit);
        log.info("counted {} primes below {}", primes, limit);
        return Map.of("limit", limit, "primes", primes);
    }

    private static int countPrimes(int limit) {
        int count = 0;
        for (int n = 2; n < limit; n++) {
            boolean prime = true;
            for (int d = 2; (long) d * d <= n; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) count++;
        }
        return count;
    }

    @GetMapping("/api/chain")
    public Map<String, Object> chain() {
        log.info("chain start");
        String hello = self.get().uri("/api/hello").retrieve().body(String.class);
        String slow = self.get().uri("/api/slow").retrieve().body(String.class);
        String cpu = self.get().uri("/api/cpu").retrieve().body(String.class);
        log.info("chain done");
        return Map.of("hello", hello, "slow", slow, "cpu", cpu);
    }

    @GetMapping("/api/error")
    public Map<String, String> error() {
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            log.error("random failure");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "random failure");
        }
        return Map.of("status", "ok");
    }
}
