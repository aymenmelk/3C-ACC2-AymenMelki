package tn.esprit.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: full Spring context + in-memory H2 database (profile "test").
 * Exercises the real controller -> service -> repository -> database chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsAndHealthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void entrepriseEquipeProjetFlow() throws Exception {
        long entrepriseId = createAndGetId("/entreprise/add", "{\"nom\":\"Esprit\",\"adresse\":\"Tunis\"}");
        long equipeId = createAndGetId("/equipe/add", "{\"nom\":\"DevOps\",\"specialite\":\"CI/CD\"}");
        long projetId = createAndGetId("/projet/add", "{\"sujet\":\"Pipeline Jenkins\"}");

        mockMvc.perform(put("/equipe/assign-entreprise/{e}/{ent}", equipeId, entrepriseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entreprise.id").value(entrepriseId));

        mockMvc.perform(put("/equipe/assign-projet/{e}/{p}", equipeId, projetId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/equipe/by-entreprise/{id}", entrepriseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nom").value("DevOps"));

        long pdId = createAndGetId("/projet-detaille/add",
                "{\"description\":\"CI\",\"technologie\":\"Spring\",\"coutProvisoire\":1500.0,\"dateDebut\":\"2026-01-15\"}");
        mockMvc.perform(put("/projet-detaille/assign-projet/{pd}/{p}", pdId, projetId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/projet-detaille/by-projet/{p}", projetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].technologie").value("Spring"));
    }

    @Test
    void projetDetailleKeepsProjetSentInPayload() throws Exception {
        long projetId = createAndGetId("/projet/add", "{\"sujet\":\"Monitoring\"}");
        long pdId = createAndGetId("/projet-detaille/add",
                "{\"description\":\"Grafana\",\"technologie\":\"Prometheus\",\"projet\":{\"id\":" + projetId + "}}");

        mockMvc.perform(get("/projet-detaille/get/{id}", pdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projet.id").value(projetId));
    }

    @Test
    void assigningUnknownEntitiesReturns404() throws Exception {
        mockMvc.perform(put("/equipe/assign-entreprise/{e}/{ent}", 9999, 9999))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/projet-detaille/assign-projet/{pd}/{p}", 9999, 9999))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateAndDeleteEntreprise() throws Exception {
        long id = createAndGetId("/entreprise/add", "{\"nom\":\"Old\",\"adresse\":\"Sfax\"}");

        mockMvc.perform(put("/entreprise/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"nom\":\"New\",\"adresse\":\"Sousse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("New"));

        mockMvc.perform(delete("/entreprise/delete/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/entreprise/get/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    private long createAndGetId(String url, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
