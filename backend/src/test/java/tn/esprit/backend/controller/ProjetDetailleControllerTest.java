package tn.esprit.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.backend.entity.ProjetDetaille;
import tn.esprit.backend.service.IProjetDetailleService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjetDetailleController.class)
class ProjetDetailleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IProjetDetailleService service;

    private ProjetDetaille sample() {
        return ProjetDetaille.builder().id(1L).description("CI").technologie("Spring").coutProvisoire(100.0).build();
    }

    @Test
    void getAll() throws Exception {
        when(service.getAllProjetsDetailles()).thenReturn(List.of(sample()));
        mockMvc.perform(get("/projet-detaille/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].technologie").value("Spring"));
    }

    @Test
    void getById() throws Exception {
        when(service.getProjetDetailleById(1L)).thenReturn(sample());
        mockMvc.perform(get("/projet-detaille/get/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technologie").value("Spring"));
    }

    @Test
    void add() throws Exception {
        when(service.addProjetDetaille(any(ProjetDetaille.class))).thenReturn(sample());
        mockMvc.perform(post("/projet-detaille/add").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"CI\",\"technologie\":\"Spring\",\"coutProvisoire\":100.0,\"dateDebut\":\"2026-01-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update() throws Exception {
        when(service.updateProjetDetaille(any(ProjetDetaille.class))).thenReturn(sample());
        mockMvc.perform(put("/projet-detaille/update").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"CI\",\"technologie\":\"Spring\",\"coutProvisoire\":100.0,\"dateDebut\":\"2026-01-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technologie").value("Spring"));
    }

    @Test
    void deleteById() throws Exception {
        mockMvc.perform(delete("/projet-detaille/delete/1")).andExpect(status().isOk());
        verify(service).deleteProjetDetaille(1L);
    }

    @Test
    void byProjetAndAssign() throws Exception {
        when(service.getProjetDetaillesByProjet(3L)).thenReturn(List.of(sample()));
        when(service.assignProjetDetailleToProjet(1L, 3L)).thenReturn(sample());
        mockMvc.perform(get("/projet-detaille/by-projet/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].technologie").value("Spring"));
        mockMvc.perform(put("/projet-detaille/assign-projet/1/3")).andExpect(status().isOk());
        verify(service).assignProjetDetailleToProjet(1L, 3L);
    }
}
