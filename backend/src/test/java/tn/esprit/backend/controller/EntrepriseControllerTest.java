package tn.esprit.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.service.IEntrepriseService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EntrepriseController.class)
class EntrepriseControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IEntrepriseService service;

    private Entreprise sample() {
        return Entreprise.builder().id(1L).nom("Acme").adresse("Tunis").build();
    }

    @Test
    void getAll() throws Exception {
        when(service.getAllEntreprises()).thenReturn(List.of(sample()));
        mockMvc.perform(get("/entreprise/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nom").value("Acme"));
    }

    @Test
    void getById() throws Exception {
        when(service.getEntrepriseById(1L)).thenReturn(sample());
        mockMvc.perform(get("/entreprise/get/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Acme"));
    }

    @Test
    void add() throws Exception {
        when(service.addEntreprise(any(Entreprise.class))).thenReturn(sample());
        mockMvc.perform(post("/entreprise/add").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Acme\",\"adresse\":\"Tunis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update() throws Exception {
        when(service.updateEntreprise(any(Entreprise.class))).thenReturn(sample());
        mockMvc.perform(put("/entreprise/update").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Acme\",\"adresse\":\"Tunis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Acme"));
    }

    @Test
    void deleteById() throws Exception {
        mockMvc.perform(delete("/entreprise/delete/1")).andExpect(status().isOk());
        verify(service).deleteEntreprise(1L);
    }

}
