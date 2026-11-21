package tn.esprit.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void eurekaRegistryAnswers() throws Exception {
        HttpResponse<String> response = get("/eureka/apps");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("applications");
    }

    @Test
    void healthIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }
}
