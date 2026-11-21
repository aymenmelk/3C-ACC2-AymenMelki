package tn.esprit.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.service.IProjetService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjetController.class)
class ProjetControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IProjetService service;

    private Projet sample() {
        return Projet.builder().id(1L).sujet("DevOps").build();
    }

    @Test
    void getAll() throws Exception {
        when(service.getAllProjets()).thenReturn(List.of(sample()));
        mockMvc.perform(get("/projet/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].sujet").value("DevOps"));
    }

    @Test
    void getById() throws Exception {
        when(service.getProjetById(1L)).thenReturn(sample());
        mockMvc.perform(get("/projet/get/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sujet").value("DevOps"));
    }

    @Test
    void add() throws Exception {
        when(service.addProjet(any(Projet.class))).thenReturn(sample());
        mockMvc.perform(post("/projet/add").contentType(MediaType.APPLICATION_JSON).content("{\"sujet\":\"DevOps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update() throws Exception {
        when(service.updateProjet(any(Projet.class))).thenReturn(sample());
        mockMvc.perform(put("/projet/update").contentType(MediaType.APPLICATION_JSON).content("{\"sujet\":\"DevOps\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sujet").value("DevOps"));
    }

    @Test
    void deleteById() throws Exception {
        mockMvc.perform(delete("/projet/delete/1")).andExpect(status().isOk());
        verify(service).deleteProjet(1L);
    }

}
