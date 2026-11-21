package tn.esprit.backend.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.entity.Equipe;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.entity.ProjetDetaille;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RepositoryTests {

    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private EquipeRepository equipeRepository;
    @Autowired private ProjetRepository projetRepository;
    @Autowired private ProjetDetailleRepository projetDetailleRepository;

    @Test
    void findEquipesByEntrepriseId() {
        Entreprise acme = entrepriseRepository.save(Entreprise.builder().nom("Acme").adresse("Tunis").build());
        Entreprise other = entrepriseRepository.save(Entreprise.builder().nom("Other").adresse("Sfax").build());
        equipeRepository.save(Equipe.builder().nom("Back").specialite("Java").entreprise(acme).build());
        equipeRepository.save(Equipe.builder().nom("Front").specialite("Angular").entreprise(acme).build());
        equipeRepository.save(Equipe.builder().nom("Ops").specialite("Docker").entreprise(other).build());

        List<Equipe> result = equipeRepository.findByEntrepriseId(acme.getId());

        assertThat(result).extracting(Equipe::getNom).containsExactlyInAnyOrder("Back", "Front");
    }

    @Test
    void findProjetsDetaillesByProjetId() {
        Projet projet = projetRepository.save(Projet.builder().sujet("DevOps").build());
        projetDetailleRepository.save(ProjetDetaille.builder()
                .description("CI").technologie("Jenkins").coutProvisoire(1000.0)
                .dateDebut(LocalDate.of(2026, 1, 1)).projet(projet).build());
        projetDetailleRepository.save(ProjetDetaille.builder()
                .description("Orphan").technologie("None").build());

        List<ProjetDetaille> result = projetDetailleRepository.findByProjetId(projet.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTechnologie()).isEqualTo("Jenkins");
    }

    @Test
    void builderInitialisesCollections() {
        assertThat(Equipe.builder().build().getProjets()).isNotNull().isEmpty();
        assertThat(Entreprise.builder().build().getEquipes()).isNotNull().isEmpty();
        assertThat(Projet.builder().build().getEquipes()).isNotNull().isEmpty();
        assertThat(Projet.builder().build().getProjetsDetailles()).isNotNull().isEmpty();
    }
}
