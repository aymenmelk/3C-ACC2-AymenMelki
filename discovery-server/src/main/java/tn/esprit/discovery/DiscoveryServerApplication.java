package tn.esprit.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Microservice "discovery-server": Eureka service registry.
 * The other microservices (backend) register here and can be discovered by name.
 * Dashboard: http://localhost:8761 (docker compose) or :30761 (Kubernetes POD).
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
