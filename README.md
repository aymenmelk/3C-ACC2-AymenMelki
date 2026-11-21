# 3C-ACC2-AymenMelki — Projet DevOps

[![CI](https://github.com/aymenmelk/3C-ACC2-AymenMelki/actions/workflows/ci.yml/badge.svg)](https://github.com/aymenmelk/3C-ACC2-AymenMelki/actions/workflows/ci.yml)

Chaîne DevOps complète autour d'une application **Spring Boot 4 + Angular 22** de gestion de projets
(Entreprises, Équipes, Projets, Projets détaillés) : **Maven → Git → SonarQube → Jenkins → Docker Hub → Kubernetes (PODs SonarQube, Spring, MySQL, Angular, Eureka, Monitoring) → Grafana**, infrastructure décrite par **Terraform** et **Ansible**.

| | |
|---|---|
| Étudiant | Aymen Melki — 3C-ACC2 |
| Repo Git (`classe_nomEtudiant`) | `3C-ACC2-AymenMelki` |
| Images Docker (`nomprenom-classe-nomProjet`) | `aymenbay/aymenmelki-3c-acc2-gestionprojets-backend` et `…-frontend` |

> Les noms d'images Docker doivent être en minuscules, d'où `aymenmelki-3c-acc2-…`.

---

## 🏗️ Architecture

```
VMware ─ VM Ubuntu (provisionnée par Ansible)
 │
 │ GitHub ──(webhook via ngrok / poll)──▶ Jenkins (conteneur, créé par docker compose ou Terraform)
 │                                          ├─▶ Maven clean/compile/test/package/install (backend + discovery-server)
 │                                          ├─▶ npm ci / test:ci / build (Angular)
 │                                          ├─▶ POD SonarQube (Code Analysis + Quality Gate)
 │                                          ├─▶ docker build ──▶ Docker Hub
 │                                          └─▶ kubectl apply -k k8s ──▶ Kubernetes (k3s)
 │                                                 ├─ POD MySQL 8.4 (+ volume persistant)
 │                                                 ├─ POD discovery-server (Eureka) :30761
 │                                                 ├─ POD Spring Boot :30080   (s'enregistre dans Eureka)
 │                                                 ├─ POD Angular/nginx :30420 (/api → Spring)
 │                                                 └─ POD Monitoring : Prometheus :30090 + Grafana :30300
 └─ namespace sonarqube : POD SonarQube :30900 + POD PostgreSQL
```

Le déploiement suit l'architecture du sujet : **un POD par livrable** (Spring, MySQL, Angular), **un POD SonarQube**
et **un POD Monitoring** contenant les conteneurs Prometheus et Grafana. Si Kubernetes n'est pas activé,
Jenkins livre la même application avec `docker compose` (paramètre `DEPLOY_TARGET`).

| Dossier / fichier | Rôle |
|---|---|
| `backend/` | API REST Spring Boot 4.1 (Java 17), MySQL, Actuator + Micrometer Prometheus, JaCoCo, client Eureka |
| `discovery-server/` | **Microservice** registre de services **Eureka** (Spring Cloud 2025.1) |
| `frontend/` | Angular 22, tests Vitest + couverture, image nginx |
| `k8s/` | **Kubernetes** : PODs MySQL, Eureka, Spring, Angular, Monitoring (Prometheus + Grafana) — `kubectl apply -k k8s` |
| `k8s/sonarqube/` | **POD SonarQube** + POD PostgreSQL (NodePort 30900) — `kubectl apply -k k8s/sonarqube` |
| `iac/terraform/` | **Terraform** (provider Docker) : réseau, volumes, images et conteneurs Jenkins / Prometheus / Grafana (+ SonarQube optionnel) |
| `iac/ansible/` | **Ansible** : `provision.yml` (VM : Git, Java, Maven, Docker, k3s, Terraform, POD SonarQube) et `configure.yml` (configuration de SonarQube, Grafana, Jenkins via leurs API) |
| `docker-compose.yml` | Livraison alternative sans Kubernetes : MySQL + backend + frontend |
| `infra/docker-compose.yml` | **Outils DevOps** : Jenkins, Prometheus, Grafana, ngrok (profil) — SonarQube en Docker disponible avec le profil `sonar-docker` |
| `infra/jenkins/` | Image Jenkins (JDK 21, Node 24, Docker CLI) + plugins + **Configuration as Code** (job créé automatiquement) |
| `infra/sonar/sonar-init.sh` | Configure SonarQube automatiquement (mot de passe admin, token Jenkins, webhook Quality Gate) |
| `infra/ubuntu-vm/setup-ubuntu.sh` | Installe toute la chaîne dans une **VM Ubuntu sur VMware** (architecture du sujet) |
| `infra/prometheus/`, `infra/grafana/` | Scraping + datasource et dashboard Grafana provisionnés |
| `Jenkinsfile` | Pipeline CI/CD |
| `.github/workflows/ci.yml` | Même chaîne sur GitHub Actions + test end-to-end de Jenkins/Sonar/Grafana |

---

## ⚙️ Prérequis (Windows)

- **Docker Desktop** (backend WSL2) avec **≥ 8 Go de RAM** (Settings → Resources) — SonarQube est gourmand
- **Kubernetes activé dans Docker Desktop** : Settings → Kubernetes → *Enable Kubernetes* (type **kubeadm**) → Apply & restart.
  Vérifier : `kubectl get nodes` → `docker-desktop   Ready`
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

### 2. Lancer le POD SonarQube puis les outils DevOps

```powershell
kubectl apply -k k8s/sonarqube
kubectl -n sonarqube rollout status deployment/sonarqube --timeout=900s
cd infra
copy .env.example .env      # puis éditer .env : DOCKERHUB_TOKEN, (optionnel) SMTP_*, NGROK_*
docker compose up -d --build
```

Le premier démarrage prend quelques minutes (téléchargement des images + plugins Jenkins).
`sonar-init` configure SonarQube tout seul, puis Jenkins démarre déjà configuré.
Sans Kubernetes : mettre `COMPOSE_PROFILES=sonar-docker` et `SONAR_URL=http://sonarqube:9000` dans `.env` (SonarQube en Docker, port 9000).

| Outil | URL | Identifiants par défaut |
|---|---|---|
| Jenkins | http://localhost:8081 | `admin` / `admin` |
| SonarQube (POD) | http://localhost:30900 | `admin` / `Admin_DevOps_2026!` |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |

### 3. Lancer le pipeline

Jenkins → job **`gestionprojets-pipeline`** → **Build Now**.
Le pipeline construit, teste, analyse, crée les images, les pousse sur Docker Hub puis **déploie sur Kubernetes** :

| POD | URL |
|---|---|
| Angular | http://localhost:30420 |
| Eureka (discovery-server) | http://localhost:30761 — **BACKEND** doit apparaître dans « Instances currently registered » |
| Spring Boot | http://localhost:30080/entreprise/all — santé : `/actuator/health`, métriques : `/actuator/prometheus` |
| Grafana (POD Monitoring) | http://localhost:30300 (`admin` / `admin`) |
| Prometheus (POD Monitoring) | http://localhost:30090 |

```powershell
kubectl -n gestionprojets get pods,svc     # les PODs et services
```

Sans Kubernetes (`DEPLOY_TARGET=docker-compose`) : frontend http://localhost:4200, backend http://localhost:8080, MySQL `localhost:3307`.

Ensuite le job vérifie GitHub toutes les 5 minutes (`pollSCM`) : chaque `git push` relance la chaîne.

### 4. Monitoring

Grafana (POD Monitoring : http://localhost:30300, ou http://localhost:3000 côté outils) → Dashboards → **DevOps → « Projet DevOps — Gestion des Projets »** :
requêtes HTTP/s, latence p95, erreurs 5xx, mémoire JVM, threads, connexions MySQL (HikariCP), état et durée des builds Jenkins.

### 🐧 Installation dans une VM Ubuntu sur VMware (architecture du sujet)

C'est l'architecture du schéma : **GitHub + Maven + Jenkins dans une VM Ubuntu (VMware)**, qui livre sur **Kubernetes**.

1. VMware Workstation → *Create a New Virtual Machine* → ISO **Ubuntu Server 24.04 LTS** (ou 26.04 LTS)
   - RAM **8 Go** (minimum 6 Go), **4 vCPU**, disque **60 Go**, réseau **NAT**
   - Pendant l'installation : cocher **Install OpenSSH server**
2. Dans la VM (connecté avec votre utilisateur, pas root) :
   ```bash
   curl -fsSL https://raw.githubusercontent.com/aymenmelk/3C-ACC2-AymenMelki/main/infra/ubuntu-vm/setup-ubuntu.sh -o setup-ubuntu.sh
   bash setup-ubuntu.sh
   ```
   Le script installe Git, Java 17, **Maven** (et lance `mvn clean install`), Docker, **Kubernetes (k3s)**,
   puis démarre Jenkins, SonarQube, Prometheus et Grafana. Il affiche à la fin les URLs avec l'IP de la VM.
3. Mettre le token Docker Hub dans `~/3C-ACC2-AymenMelki/infra/.env`, puis `sg docker -c "docker compose up -d jenkins"`.
4. Depuis Windows : `http://<IP-de-la-VM>:8081` → **Build Now**.

> Avec la VM, arrêtez la pile Docker Desktop sous Windows (`docker compose down` dans `infra`) pour libérer la RAM.

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
| Microservice: discovery-server | `./mvnw clean install` du registre Eureka (tests + JaCoCo) |
| Frontend | `npm ci`, `npm run test:ci` (Vitest + lcov), `npm run build` |
| SonarQube | `npx @sonar/scan` (lit `sonar-project.properties`) |
| Quality Gate | `waitForQualityGate` (webhook Sonar → Jenkins créé automatiquement) |
| Docker build | `aymenbay/aymenmelki-3c-acc2-gestionprojets-{backend,frontend,discovery}:<build>` + `latest` |
| Docker push | Docker Hub (identifiants `dockerhub-creds` depuis `infra/.env`) |
| Select deploy target | `auto` : Kubernetes s'il est joignable, sinon docker compose |
| Deploy: Kubernetes | `kubectl apply -k k8s` avec le tag du build + `rollout status` des PODs |
| Deploy: docker compose | alternative : `docker compose up -d` |
| Smoke test | santé Spring, API, frontend + proxy `/api`, métriques, enregistrement dans Eureka, Grafana et Prometheus |
| Notification | e-mail si `NOTIFY_EMAIL` + `SMTP_*` sont renseignés |

### Améliorations du sujet réalisées

| Amélioration | Réalisation |
|---|---|
| **Kubernetes** | PODs Spring, MySQL, Angular, Eureka, Monitoring et **SonarQube**, déployés par Jenkins (`kubectl`) |
| **Angular dans un conteneur** | image nginx, proxy `/api` vers le POD Spring |
| **Microservices** | microservice `discovery-server` (registre **Eureka**, guide *service-registration-and-discovery*) ; le backend s'y enregistre |
| **IaC — Terraform** | `iac/terraform` : les conteneurs Docker de la chaîne (Jenkins, Prometheus, Grafana, SonarQube optionnel) |
| **IaC — Ansible** | `iac/ansible/provision.yml` prépare la VM ; `configure.yml` configure les outils dans les conteneurs (SonarQube, Grafana, Jenkins) |
| **Webhook + ngrok + Jenkins distant** | profil `ngrok` : URL publique fixe vers Jenkins ; webhook GitHub `…/github-webhook/` → build à chaque `git push` |
| **Notification e-mail** | Jenkins envoie le résultat de chaque build (Gmail, mot de passe d'application) |
| **Jenkins as Code** | JCasC + Job DSL : utilisateur, credentials, SonarQube, e-mail et job créés automatiquement |
| **CI GitHub Actions** | rejoue toute la chaîne : tests, Docker, Kubernetes (kind), Terraform + Ansible, Jenkinsfile réel |

### 🧱 IaC : Terraform + Ansible (dans la VM)

```bash
# 1. Ansible prépare la VM (Git, Java, Maven, Docker, k3s, Terraform, POD SonarQube)
cd ~/3C-ACC2-AymenMelki/iac/ansible && ansible-playbook provision.yml -K

# 2. Terraform crée les conteneurs de la chaîne (alternative à docker compose : arrêter compose avant)
cd ../terraform && cp terraform.tfvars.example terraform.tfvars   # token Docker Hub...
terraform init && terraform apply

# 3. Ansible configure les outils dans les conteneurs (projet + Quality Gate SonarQube, Grafana, Jenkins)
cd ../ansible && ansible-playbook configure.yml
```

### 🌍 Webhook GitHub via ngrok (Jenkins distant)

1. Compte gratuit https://dashboard.ngrok.com : copier l'**Authtoken** et créer le **domaine statique** gratuit.
2. Dans `infra/.env` :
   ```
   COMPOSE_PROFILES=ngrok
   NGROK_AUTHTOKEN=...
   NGROK_DOMAIN=xxxx.ngrok-free.app
   JENKINS_URL=https://xxxx.ngrok-free.app/
   ```
3. `docker compose up -d` → Jenkins est accessible de partout sur `https://xxxx.ngrok-free.app`.
4. GitHub → repo → *Settings → Webhooks → Add webhook* : `https://xxxx.ngrok-free.app/github-webhook/`, `application/json`, *Just the push event*.

### ✉️ Notifications e-mail

Dans `infra/.env` (Gmail : validation en 2 étapes + mot de passe d'application) puis `docker compose up -d jenkins` :
```
NOTIFY_EMAIL=vous@gmail.com
SMTP_USER=vous@gmail.com
SMTP_PASSWORD=<mot de passe d'application>
SMTP_FROM=vous@gmail.com
```

---

## 🧪 Tests

| | Contenu | Commande |
|---|---|---|
| Backend — unitaires | Services (Mockito), cas « introuvable » → 404 | `cd backend && ./mvnw test` |
| Backend — web | Les 4 contrôleurs REST (`@WebMvcTest` + MockMvc) | |
| Backend — JPA | Requêtes dérivées des repositories (`@DataJpaTest`, H2) | |
| Backend — intégration | Contexte complet + H2 (mode MySQL) : CRUD, affectations, actuator | |
| Frontend | 4 services HTTP + 4 pages + composant racine (39 tests) | `cd frontend && npm run test:ci` |
| discovery-server | registre Eureka + santé (`@SpringBootTest`) | `cd discovery-server && ./mvnw test` |

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
| Jenkins déploie avec docker compose au lieu de Kubernetes | Kubernetes non activé/joignable : vérifier `kubectl get nodes`, puis `docker compose up -d jenkins` dans `infra` |
| Un POD ne démarre pas | `kubectl -n gestionprojets get pods` puis `kubectl -n gestionprojets logs deploy/backend` |

---

Base du projet : application de gestion de projets fournie pour le module DevOps (ESPRIT).
