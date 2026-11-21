package tn.esprit.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.repository.EntrepriseRepository;
import tn.esprit.backend.service.impl.EntrepriseServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntrepriseServiceImplTest {

    @Mock private EntrepriseRepository repository;
    @InjectMocks private EntrepriseServiceImpl service;

    private final Entreprise acme = Entreprise.builder().id(1L).nom("Acme").adresse("Tunis").build();

    @Test
    void addEntreprise() {
        when(repository.save(acme)).thenReturn(acme);
        assertThat(service.addEntreprise(acme)).isSameAs(acme);
        verify(repository).save(acme);
    }

    @Test
    void updateEntreprise() {
        when(repository.save(acme)).thenReturn(acme);
        assertThat(service.updateEntreprise(acme)).isSameAs(acme);
    }

    @Test
    void deleteEntreprise() {
        service.deleteEntreprise(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void getEntrepriseByIdFoundAndNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(acme));
        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThat(service.getEntrepriseById(1L)).isSameAs(acme);
        assertThat(service.getEntrepriseById(2L)).isNull();
    }

    @Test
    void getAllEntreprises() {
        when(repository.findAll()).thenReturn(List.of(acme));
        assertThat(service.getAllEntreprises()).containsExactly(acme);
    }
}
