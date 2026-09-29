package demo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class DemoServlets {

    private static final Logger log = Logger.getLogger(DemoServlets.class.getName());

    private static final String JAVA_APP = System.getenv().getOrDefault("JAVA_APP_URL", "http://java-app:8080");
    private static final String DB_URL = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://postgres:5432/grafana");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "grafana");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "grafana");

    private static final HttpClient http = HttpClient.newHttpClient();

    private DemoServlets() {
    }

    private static void text(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        resp.getWriter().println(body);
    }

    @WebServlet("/hello")
    public static class Hello extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            log.info("tomcat hello called");
            text(resp, "hello from tomcat-app");
        }
    }

    // tomcat-app → java-app 호출 (HttpClient 는 OTel agent 가 계측해 traceparent 전파)
    @WebServlet("/call")
    public static class Call extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            StringBuilder out = new StringBuilder();
            for (String path : new String[] {"/api/hello", "/api/cpu"}) {
                try {
                    HttpResponse<String> r = http.send(
                            HttpRequest.newBuilder(URI.create(JAVA_APP + path)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    out.append(path).append(" -> ").append(r.statusCode()).append(' ').append(r.body()).append('\n');
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            log.info("tomcat call finished");
            text(resp, out.toString());
        }
    }

    // JDBC 조회 (OTel agent 가 DB span 생성)
    @WebServlet("/db")
    public static class Db extends HttpServlet {
        // WEB-INF/lib 의 드라이버는 DriverManager 가 자동 등록하지 않으므로 명시적으로 로드
        @Override
        public void init() {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT count(*) AS tables, now() AS ts FROM information_schema.tables WHERE table_schema = 'public'")) {
                rs.next();
                String body = "tables=" + rs.getLong("tables") + " ts=" + rs.getTimestamp("ts");
                log.info("tomcat db query: " + body);
                text(resp, body);
            } catch (SQLException e) {
                log.severe("db query failed: " + e.getMessage());
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
    }
}
