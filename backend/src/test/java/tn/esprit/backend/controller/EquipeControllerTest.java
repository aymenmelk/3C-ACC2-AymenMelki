package tn.esprit.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.backend.entity.Equipe;
import tn.esprit.backend.service.IEquipeService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EquipeController.class)
class EquipeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IEquipeService service;

    private Equipe sample() {
        return Equipe.builder().id(1L).nom("Back").specialite("Java").build();
    }

    @Test
    void getAll() throws Exception {
        when(service.getAllEquipes()).thenReturn(List.of(sample()));
        mockMvc.perform(get("/equipe/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nom").value("Back"));
    }

    @Test
    void getById() throws Exception {
        when(service.getEquipeById(1L)).thenReturn(sample());
        mockMvc.perform(get("/equipe/get/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Back"));
    }

    @Test
    void add() throws Exception {
        when(service.addEquipe(any(Equipe.class))).thenReturn(sample());
        mockMvc.perform(post("/equipe/add").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Back\",\"specialite\":\"Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update() throws Exception {
        when(service.updateEquipe(any(Equipe.class))).thenReturn(sample());
        mockMvc.perform(put("/equipe/update").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Back\",\"specialite\":\"Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Back"));
    }

    @Test
    void deleteById() throws Exception {
        mockMvc.perform(delete("/equipe/delete/1")).andExpect(status().isOk());
        verify(service).deleteEquipe(1L);
    }

    @Test
    void byEntreprise() throws Exception {
        when(service.getEquipesByEntreprise(2L)).thenReturn(List.of(sample()));
        mockMvc.perform(get("/equipe/by-entreprise/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Back"));
    }

    @Test
    void assignEntrepriseAndProjet() throws Exception {
        when(service.assignEquipeToEntreprise(1L, 2L)).thenReturn(sample());
        when(service.assignEquipeToProjet(1L, 3L)).thenReturn(sample());
        mockMvc.perform(put("/equipe/assign-entreprise/1/2")).andExpect(status().isOk());
        mockMvc.perform(put("/equipe/assign-projet/1/3")).andExpect(status().isOk());
        verify(service).assignEquipeToEntreprise(1L, 2L);
        verify(service).assignEquipeToProjet(1L, 3L);
    }

    @Test
    void assignUnknownReturns404() throws Exception {
        when(service.assignEquipeToEntreprise(9L, 9L))
                .thenThrow(new tn.esprit.backend.exception.ResourceNotFoundException("Equipe", 9L));
        mockMvc.perform(put("/equipe/assign-entreprise/9/9")).andExpect(status().isNotFound());
    }
}
