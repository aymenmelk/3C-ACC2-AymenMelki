package tn.esprit.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "projets_detailles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjetDetaille {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    private String technologie;

    private Double coutProvisoire;

    private LocalDate dateDebut;

    // Serialized (Projet's own collections are @JsonIgnore, so no cycle) so the frontend can read/send projet.id
    @ManyToOne
    private Projet projet;
}
