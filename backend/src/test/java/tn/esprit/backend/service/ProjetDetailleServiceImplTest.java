package tn.esprit.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.entity.ProjetDetaille;
import tn.esprit.backend.exception.ResourceNotFoundException;
import tn.esprit.backend.repository.ProjetDetailleRepository;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.impl.ProjetDetailleServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjetDetailleServiceImplTest {

    @Mock private ProjetDetailleRepository projetDetailleRepository;
    @Mock private ProjetRepository projetRepository;
    @InjectMocks private ProjetDetailleServiceImpl service;

    private ProjetDetaille newPd() {
        return ProjetDetaille.builder().id(1L).description("CI").technologie("Spring")
                .coutProvisoire(1200.0).dateDebut(LocalDate.of(2026, 2, 1)).build();
    }

    @Test
    void crudDelegatesToRepository() {
        ProjetDetaille pd = newPd();
        when(projetDetailleRepository.save(pd)).thenReturn(pd);
        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.of(pd));
        when(projetDetailleRepository.findAll()).thenReturn(List.of(pd));
        when(projetDetailleRepository.findByProjetId(7L)).thenReturn(List.of(pd));

        assertThat(service.addProjetDetaille(pd)).isSameAs(pd);
        assertThat(service.updateProjetDetaille(pd)).isSameAs(pd);
        assertThat(service.getProjetDetailleById(1L)).isSameAs(pd);
        assertThat(service.getAllProjetsDetailles()).containsExactly(pd);
        assertThat(service.getProjetDetaillesByProjet(7L)).containsExactly(pd);
        service.deleteProjetDetaille(1L);
        verify(projetDetailleRepository).deleteById(1L);
    }

    @Test
    void getByIdReturnsNullWhenMissing() {
        when(projetDetailleRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(service.getProjetDetailleById(9L)).isNull();
    }

    @Test
    void assignProjetDetailleToProjet() {
        ProjetDetaille pd = newPd();
        Projet projet = Projet.builder().id(7L).sujet("DevOps").build();
        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.of(pd));
        when(projetRepository.findById(7L)).thenReturn(Optional.of(projet));
        when(projetDetailleRepository.save(any(ProjetDetaille.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.assignProjetDetailleToProjet(1L, 7L).getProjet()).isSameAs(projet);
    }

    @Test
    void assignThrowsWhenEitherSideMissing() {
        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignProjetDetailleToProjet(1L, 7L))
                .isInstanceOf(ResourceNotFoundException.class);

        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.of(newPd()));
        when(projetRepository.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignProjetDetailleToProjet(1L, 7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Projet");
    }
}
