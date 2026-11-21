package tn.esprit.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.impl.ProjetServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjetServiceImplTest {

    @Mock private ProjetRepository repository;
    @InjectMocks private ProjetServiceImpl service;

    private final Projet projet = Projet.builder().id(1L).sujet("DevOps").build();

    @Test
    void addAndUpdateProjet() {
        when(repository.save(projet)).thenReturn(projet);
        assertThat(service.addProjet(projet)).isSameAs(projet);
        assertThat(service.updateProjet(projet)).isSameAs(projet);
        verify(repository, times(2)).save(projet);
    }

    @Test
    void deleteProjet() {
        service.deleteProjet(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void getProjetById() {
        when(repository.findById(1L)).thenReturn(Optional.of(projet));
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThat(service.getProjetById(1L)).isSameAs(projet);
        assertThat(service.getProjetById(9L)).isNull();
    }

    @Test
    void getAllProjets() {
        when(repository.findAll()).thenReturn(List.of(projet));
        assertThat(service.getAllProjets()).containsExactly(projet);
    }
}
