package tn.esprit.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.entity.Equipe;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.exception.ResourceNotFoundException;
import tn.esprit.backend.repository.EntrepriseRepository;
import tn.esprit.backend.repository.EquipeRepository;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.impl.EquipeServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipeServiceImplTest {

    @Mock private EquipeRepository equipeRepository;
    @Mock private EntrepriseRepository entrepriseRepository;
    @Mock private ProjetRepository projetRepository;
    @InjectMocks private EquipeServiceImpl service;

    private Equipe newEquipe() {
        return Equipe.builder().id(1L).nom("DevOps").specialite("CI").build();
    }

    @Test
    void crudDelegatesToRepository() {
        Equipe equipe = newEquipe();
        when(equipeRepository.save(equipe)).thenReturn(equipe);
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(equipeRepository.findAll()).thenReturn(List.of(equipe));

        assertThat(service.addEquipe(equipe)).isSameAs(equipe);
        assertThat(service.updateEquipe(equipe)).isSameAs(equipe);
        assertThat(service.getEquipeById(1L)).isSameAs(equipe);
        assertThat(service.getAllEquipes()).containsExactly(equipe);
        service.deleteEquipe(1L);
        verify(equipeRepository).deleteById(1L);
    }

    @Test
    void getEquipeByIdReturnsNullWhenMissing() {
        when(equipeRepository.findById(5L)).thenReturn(Optional.empty());
        assertThat(service.getEquipeById(5L)).isNull();
    }

    @Test
    void getEquipesByEntreprise() {
        Equipe equipe = newEquipe();
        when(equipeRepository.findByEntrepriseId(3L)).thenReturn(List.of(equipe));
        assertThat(service.getEquipesByEntreprise(3L)).containsExactly(equipe);
    }

    @Test
    void assignEquipeToEntreprise() {
        Equipe equipe = newEquipe();
        Entreprise entreprise = Entreprise.builder().id(2L).nom("Acme").build();
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(entrepriseRepository.findById(2L)).thenReturn(Optional.of(entreprise));
        when(equipeRepository.save(any(Equipe.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipe result = service.assignEquipeToEntreprise(1L, 2L);

        assertThat(result.getEntreprise()).isSameAs(entreprise);
    }

    @Test
    void assignEquipeToEntrepriseThrowsWhenMissing() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignEquipeToEntreprise(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Equipe");

        when(equipeRepository.findById(1L)).thenReturn(Optional.of(newEquipe()));
        when(entrepriseRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignEquipeToEntreprise(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entreprise");
        verify(equipeRepository, never()).save(any());
    }

    @Test
    void assignEquipeToProjetAddsOnlyOnce() {
        Equipe equipe = newEquipe();
        Projet projet = Projet.builder().id(4L).sujet("Jenkins").build();
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(equipe));
        when(projetRepository.findById(4L)).thenReturn(Optional.of(projet));
        when(equipeRepository.save(any(Equipe.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignEquipeToProjet(1L, 4L);
        Equipe result = service.assignEquipeToProjet(1L, 4L);

        assertThat(result.getProjets()).containsExactly(projet);
    }

    @Test
    void assignEquipeToProjetThrowsWhenProjetMissing() {
        when(equipeRepository.findById(1L)).thenReturn(Optional.of(newEquipe()));
        when(projetRepository.findById(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignEquipeToProjet(1L, 4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Projet");
    }
}
