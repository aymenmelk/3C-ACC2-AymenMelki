# 3C-ACC2-AymenMelki — Projet DevOps

[![CI](https://github.com/aymenmelk/3C-ACC2-AymenMelki/actions/workflows/ci.yml/badge.svg)](https://github.com/aymenmelk/3C-ACC2-AymenMelki/actions/workflows/ci.yml)

Chaîne DevOps complète autour d'une application **Spring Boot 4 + Angular 22** de gestion de projets
(Entreprises, Équipes, Projets, Projets détaillés) : **Maven → Git → SonarQube → Jenkins → Docker (Spring + MySQL) → Grafana**.

| | |
|---|---|
| Étudiant | Aymen Melki — 3C-ACC2 |
| Repo Git (`classe_nomEtudiant`) | `3C-ACC2-AymenMelki` |
| Images Docker (`nomprenom-classe-nomProjet`) | `aymenmelk/aymenmelki-3c-acc2-gestionprojets-backend` et `…-frontend` |

> Les noms d'images Docker doivent être en minuscules, d'où `aymenmelki-3c-acc2-…`.

---

## 🏗️ Architecture

```
GitHub ──(poll / webhook)──▶ Jenkins ──▶ mvnw clean/compile/test/package/install
                                │      ──▶ npm ci / test:ci / build
                                │      ──▶ SonarQube (+ Quality Gate, couverture JaCoCo + lcov)
                                │      ──▶ docker build ──▶ Docker Hub
                                └──────▶ docker compose up ──▶ MySQL 8.4 + Spring Boot (8080) + Angular/nginx (4200)
                                                                        │ /actuator/prometheus
                                                 Prometheus (9090) ◀────┘  ◀── Jenkins /prometheus
                                                        │
                                                 Grafana (3000) — dashboard « Projet DevOps » provisionné
```

| Dossier / fichier | Rôle |
|---|---|
| `backend/` | API REST Spring Boot 4.1 (Java 17), MySQL, Actuator + Micrometer Prometheus, JaCoCo |
| `frontend/` | Angular 22, tests Vitest + couverture, image nginx |
| `docker-compose.yml` | **Livraison continue** : MySQL + backend + frontend |
| `infra/docker-compose.yml` | **Outils DevOps** : Jenkins, SonarQube (+ PostgreSQL), Prometheus, Grafana |
| `infra/jenkins/` | Image Jenkins (JDK 21, Node 24, Docker CLI) + plugins + **Configuration as Code** (job créé automatiquement) |
| `infra/sonar/sonar-init.sh` | Configure SonarQube automatiquement (mot de passe admin, token Jenkins, webhook Quality Gate) |
| `infra/prometheus/`, `infra/grafana/` | Scraping + datasource et dashboard Grafana provisionnés |
| `Jenkinsfile` | Pipeline CI/CD |
| `.github/workflows/ci.yml` | Même chaîne sur GitHub Actions + test end-to-end de Jenkins/Sonar/Grafana |

---

## ⚙️ Prérequis (Windows)

- **Docker Desktop** (backend WSL2) avec **≥ 6 Go de RAM** (Settings → Resources) — SonarQube est gourmand
- **Git**
- Pour lancer sans Docker uniquement : JDK 17+ et Node ≥ 22.22 / 24.15

Rien d'autre à installer : Jenkins, SonarQube, MySQL, Prometheus et Grafana tournent en conteneurs, et Maven est fourni par `mvnw`.

---

## 🚀 Démarrage rapide

### 1. Cloner

```powershell
git clone https://github.com/aymenmelk/3C-ACC2-AymenMelki.git
cd 3C-ACC2-AymenMelki
```

### 2. Lancer les outils DevOps (Jenkins + SonarQube + Prometheus + Grafana)

```powershell
cd infra
copy .env.example .env      # puis éditer .env : DOCKERHUB_TOKEN, (optionnel) SMTP_*
docker compose up -d --build
```

Le premier démarrage prend quelques minutes (téléchargement des images + plugins Jenkins).
`sonar-init` configure SonarQube tout seul, puis Jenkins démarre déjà configuré.

| Outil | URL | Identifiants par défaut |
|---|---|---|
| Jenkins | http://localhost:8081 | `admin` / `admin` |
| SonarQube | http://localhost:9000 | `admin` / `Admin_DevOps_2026!` |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |

### 3. Lancer le pipeline

Jenkins → job **`gestionprojets-pipeline`** → **Build Now**.
Le pipeline construit, teste, analyse, crée les images, les pousse sur Docker Hub puis **déploie** l'application :

- Frontend : http://localhost:4200
- Backend : http://localhost:8080/entreprise/all
- Santé / métriques : http://localhost:8080/actuator/health, http://localhost:8080/actuator/prometheus
- MySQL : `localhost:3307` (3307 car un MySQL local occupe souvent 3306)

Ensuite le job vérifie GitHub toutes les 5 minutes (`pollSCM`) : chaque `git push` relance la chaîne.

### 4. Monitoring

Grafana → Dashboards → **DevOps → « Projet DevOps — Gestion des Projets »** :
requêtes HTTP/s, latence p95, erreurs 5xx, mémoire JVM, threads, connexions MySQL (HikariCP), état et durée des builds Jenkins.

### Lancer seulement l'application (sans Jenkins)

```powershell
docker compose up -d --build
```

---

## 🔁 Le pipeline Jenkins

| Étape | Commande |
|---|---|
| Checkout | `checkout scm` |
| Maven : clean → compile → test → package → install | `./mvnw clean`, `compile`, `test` (rapports JUnit + JaCoCo), `package`, `install` |
| Frontend | `npm ci`, `npm run test:ci` (Vitest + lcov), `npm run build` |
| SonarQube | `npx @sonar/scan` (lit `sonar-project.properties`) |
| Quality Gate | `waitForQualityGate` (webhook Sonar → Jenkins créé automatiquement) |
| Docker build | `aymenmelk/aymenmelki-3c-acc2-gestionprojets-{backend,frontend}:<build>` + `latest` |
| Docker push | Docker Hub (identifiants `dockerhub-creds` depuis `infra/.env`) |
| Deploy | `docker compose up -d` (Spring + MySQL + Angular) |
| Smoke test | `/actuator/health`, `/entreprise/all`, frontend, métriques Prometheus |
| Notification | e-mail si `NOTIFY_EMAIL` + `SMTP_*` sont renseignés |

### Améliorations du sujet réalisées

- ✅ Notification e-mail Jenkins (Gmail : utiliser un *mot de passe d'application*)
- ✅ Webhook : trigger `githubPush()` dans le Jenkinsfile. Jenkins tournant en local, exposez-le avec **ngrok** :
  `ngrok http 8081`, puis GitHub → Settings → Webhooks → `https://<id>.ngrok-free.app/github-webhook/` (content type `application/json`)
- ✅ IaC : Jenkins entièrement configuré par code (JCasC + Job DSL), SonarQube configuré par script, Grafana provisionné
- ✅ Partie Angular déployée dans un conteneur Docker (nginx)
- ✅ CI GitHub Actions qui rejoue la même chaîne de bout en bout

---

## 🧪 Tests

| | Contenu | Commande |
|---|---|---|
| Backend — unitaires | Services (Mockito), cas « introuvable » → 404 | `cd backend && ./mvnw test` |
| Backend — web | Les 4 contrôleurs REST (`@WebMvcTest` + MockMvc) | |
| Backend — JPA | Requêtes dérivées des repositories (`@DataJpaTest`, H2) | |
| Backend — intégration | Contexte complet + H2 (mode MySQL) : CRUD, affectations, actuator | |
| Frontend | 4 services HTTP + 4 pages + composant racine (39 tests) | `cd frontend && npm run test:ci` |

Rapports de couverture : `backend/target/site/jacoco/index.html` et `frontend/coverage/frontend/lcov-report/index.html`.

---

## 🖥️ Lancer en local sans Docker

```powershell
# Backend (MySQL local sur 3306, utilisateur root/root par défaut, base créée automatiquement)
cd backend
$env:SPRING_DATASOURCE_PASSWORD="votre_mot_de_passe_mysql"
.\mvnw.cmd spring-boot:run

# Frontend (autre terminal)
cd frontend
npm install
npm start          # http://localhost:4200
```

---

## 🔌 Endpoints REST disponibles

### Entreprise — `/entreprise`
| Méthode | URL | Description |
|---|---|---|
| GET | `/entreprise/all` | Lister toutes les entreprises |
| GET | `/entreprise/get/{id}` | Obtenir une entreprise par ID |
| POST | `/entreprise/add` | Ajouter une entreprise |
| PUT | `/entreprise/update` | Modifier une entreprise |
| DELETE | `/entreprise/delete/{id}` | Supprimer une entreprise |

### Equipe — `/equipe`
| Méthode | URL | Description |
|---|---|---|
| GET | `/equipe/all` | Lister toutes les équipes |
| GET | `/equipe/get/{id}` | Obtenir une équipe par ID |
| POST | `/equipe/add` | Ajouter une équipe |
| PUT | `/equipe/update` | Modifier une équipe |
| DELETE | `/equipe/delete/{id}` | Supprimer une équipe |
| PUT | `/equipe/assign-entreprise/{equipeId}/{entrepriseId}` | Affecter une équipe à une entreprise |
| PUT | `/equipe/assign-projet/{equipeId}/{projetId}` | Affecter une équipe à un projet |

### Projet — `/projet`
| Méthode | URL | Description |
|---|---|---|
| GET | `/projet/all` | Lister tous les projets |
| GET | `/projet/get/{id}` | Obtenir un projet par ID |
| POST | `/projet/add` | Ajouter un projet |
| PUT | `/projet/update` | Modifier un projet |
| DELETE | `/projet/delete/{id}` | Supprimer un projet |

### Projet Détaillé — `/projet-detaille`
| Méthode | URL | Description |
|---|---|---|
| GET | `/projet-detaille/all` | Lister tous les projets détaillés |
| GET | `/projet-detaille/get/{id}` | Obtenir un projet d��taillé par ID |
| POST | `/projet-detaille/add` | Ajouter un projet détaillé |
| PUT | `/projet-detaille/update` | Modifier un projet détaillé |
| DELETE | `/projet-detaille/delete/{id}` | Supprimer un projet détaillé |
| PUT | `/projet-detaille/assign-projet/{pdId}/{projetId}` | Affecter à un projet |

---

Les routes `assign-*` renvoient **404** si l'un des identifiants n'existe pas.
Endpoints de supervision : `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`.

---

## 🛠️ Dépannage

| Problème | Solution |
|---|---|
| SonarQube redémarre en boucle | Augmenter la RAM de Docker Desktop (≥ 6 Go) |
| `Quality Gate` bloque | Vérifier `docker logs sonar-init` (webhook créé ?) |
| Port déjà utilisé (3306/8080/…) | Changer le port côté hôte dans le `docker-compose.yml` (ex. `MYSQL_HOST_PORT`) |
| `docker push` échoue → build UNSTABLE | Renseigner `DOCKERHUB_TOKEN` dans `infra/.env` puis `docker compose up -d jenkins` |
| Repo GitHub privé | Rendre le repo public, ou ajouter des identifiants GitHub au job |

---

Base du projet : application de gestion de projets fournie pour le module DevOps (ESPRIT).
