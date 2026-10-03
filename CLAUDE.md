# CLAUDE.md — CRM (Customer Relation System)

Application CRM de gestion des candidatures et du suivi d'admission, en architecture
microservices. Ce fichier donne le contexte de travail pour tout agent IA ou contributeur.

---

## 1. Stack

| Couche | Technologie |
|---|---|
| Backend | Java 17 · Spring Boot 3.3.5 · Spring Cloud 2023.0.3 · Maven Wrapper |
| Service discovery | Spring Cloud Netflix Eureka |
| Gateway | Spring Cloud Gateway (routage `lb://`) |
| Persistance | MySQL 8 (une base par service) · Spring Data JPA · `ddl-auto: update` |
| Sécurité | Spring Security + JWT (jjwt 0.11.5) · BCrypt |
| Frontend | Angular 18 (standalone components) · TypeScript 5.4 · RxJS 7.8 |
| UI | PrimeNG 17 + Angular Material 18 + Syncfusion EJ2 Schedule + Chart.js |
| Conteneurisation | Docker multi-stage · Docker Compose |

> **Cohérence à maintenir** : tous les services sont désormais alignés sur Spring Boot
> **3.3.5** / Spring Cloud **2023.0.3** (uniformisation faite dans le commit `48997d2`).

---

## 2. Architecture

```
Front Angular :4200  ──►  nginx :80 (image Docker)
                          │
                          ▼
              api-gateway :8060          (Spring Cloud Gateway)
                          │  lb://  (résolution via Eureka :8761)
        ┌─────────────────┼──────────────────┬─────────────────────┐
        ▼                 ▼                  ▼                     ▼
   user-service     student-service     task-service      notification-service
   :8080 / authdb   :8082 / candidate   :8084 / task       :8085 / stateless
        │                 │                  │
        └─────────────────┴──────────────────┴──────►  event-service :8089 / event_system
                                                          (⚠ NON routé par la gateway)
```

### Table des services

| Dossier | Artefact Maven | Port | Nom Eureka | Base MySQL |
|---|---|---|---|---|
| `CRM_Backend/api-gateway` | `api-gateway` | 8060 | `api-gateway` | — |
| `CRM_Backend/service-discovery` | `service-discovery` | 8761 | `service-registry` | — |
| `CRM_Backend/auth-service` | `Auth_Api1` | 8080 | **`user-service`** | `authdb` |
| `CRM_Backend/Student_Management_Service` | `Application_Management_Service` | 8082 | `student-service` | `candidate` |
| `CRM_Backend/Task_Management_Service` | `Task_Management_Service` | 8084 | `task-service` | `task` |
| `CRM_Backend/Notification_Service` | `Notification_Service` | 8085 | `notification-service` | — |
| `CRM_Backend/Event_Management_System` | `Event_Management_System` | 8089 | `event-service` | `event_system` |

> ⚠️ Le nom Eureka de `auth-service` est **`user-service`** (cf. `spring.application.name`),
> et son `context-path` est **`/api/v1/`**. La route gateway `lb://user-service` est donc
> correcte — ne pas « corriger » en `lb://auth-service`.

### Routes de l'API Gateway (`api-gateway/src/main/resources/application.yml`)

| Path | Destination |
|---|---|
| `/tasks/**` | `lb://task-service` |
| `/api/candidates/**` | `lb://student-service` |
| `/api/v1/**` | `lb://user-service` |
| `/api/notifications/**` | `lb://notification-service` |
| `/api/events/**`, `/api/venues/**`, `/api/event-types/**`, `/api/contacts/**` | **manquant** → `lb://event-service` |

### Communications inter-services

Aucun OpenFeign. Tout passe par `RestTemplate` avec des **URLs aujourd'hui codées en dur
sur `localhost`** (7 occurrences). Sous Docker `localhost` = le conteneur lui-même, sous
Kubernetes = le pod lui-même → **les appels cassent dès qu'on conteneurise**.

```
task-service    ──► student-service       GET  :8082/api/candidates/{fullname}/exists
                ──► api-gateway :8060     GET  /api/v1/auth/{user}/exists
                ──► api-gateway :8060     GET  /api/v1/auth/{user}/email
                ──► notification-service  POST /api/notifications/task-assignment
                ──► notification-service  POST /api/notifications/task-completion
student-service ──► notification-service  POST /api/notifications/email | /sms
```

**Règle à respecter :** toute URL inter-services doit être externalisée en propriété
avec valeur par défaut, ex. `${STUDENT_SERVICE_URL:http://localhost:8082}`, et les
`RestTemplate` doivent avoir un timeout configuré.

---

## 3. Commandes

Toujours utiliser le **Maven Wrapper** (`mvn` n'est pas installé sur cette machine).
Sous Windows : `mvnw.cmd`. Sous Linux/macOS : `./mvnw`.

### Backend — par microservice

```bash
cd CRM_Backend/<service>

./mvnw clean verify            # compile + tests unitaires + intégration + JaCoCo
./mvnw test                    # tests unitaires uniquement
./mvnw spring-boot:run         # démarre sur le port du service

docker build -t crm/<nom> .
```

### Frontend

```bash
cd Front/CRM

npm ci                         # install reproductible (ne jamais `npm install` en CI)
npm start                      # ng serve, port 4200
npm run build                  # build production → dist/crm/browser
npm test                       # Karma + Jasmine
```

### Stack complète

```bash
docker compose up --build      # 8 services sur le réseau bridge `crm-network`
docker compose ps              # vérifier que tous les services sont `healthy`
```

### Reconstruction propre

```bash
docker compose down -v         # -v supprime aussi le volume MySQL
```

---

## 4. Variables d'environnement

Toutes lues via `${...}` dans les `application.yml` ou `docker-compose.yml`.
**Aucun fichier `.env` n'est versionné** (voir `.gitignore`) → il faut un `.env.example`.

| Variable | Utilisée par | Défaut | Remarque |
|---|---|---|---|
| `DB_USERNAME` | student, task, event | `root` | |
| `DB_PASSWORD` | auth, student, task, event | **aucun** | bloquant sans valeur |
| `JWT_SECRET_KEY` | auth | `change-me-in-env` | **doit être du Base64 valide ≥ 256 bits** ; la valeur par défaut est invalide |
| `MAIL_USERNAME` | auth, notification | **aucun** | bloquant |
| `MAIL_PASSWORD` | auth, notification | **aucun** | bloquant |
| `TWILIO_ACCOUNT_SID` | notification | **aucun** | bloquant : `@PostConstruct` échoue → le service ne démarre pas |
| `TWILIO_AUTH_TOKEN` | notification | **aucun** | idem |
| `TWILIO_PHONE_NUMBER` | notification | **aucun** | idem |
| `CORS_ALLOWED_ORIGIN` | api-gateway | `http://localhost:4200` | |

> Les `application.yml` importent `.env` via deux chemins relatifs :
> `optional:file:.env[.properties],optional:file:../../.env[.properties]`
> (le second sert à remonter à la racine du repo depuis un microservice).

---

## 5. Conventions de code

### Backend (par microservice)

```
src/main/java/<package>/
├── <Service>Application.java     @SpringBootApplication + @EnableDiscoveryClient
├── controller/                   @RestController, un contrôleur par agrégat
├── service/                      interfaces (Event_Management) ou classes (@Service ailleurs)
├── repository/                   JpaRepository
├── model/  ou  models/           entités JPA + DTO.request / DTO.response
├── config/                       @Configuration, @Bean RestTemplate
├── Exception*/  handle/          exceptions métier + @RestControllerAdvice
└── Utils/                        helpers stateless
```

- **DTO d'entrée/sortie** pour les contrôleurs. Exception connue : `AuthenticationController`
  accepte une entité JPA `User` en `@RequestBody` → ne pas propager ce pattern.
- **Entités** : `@Data` + Lombok (Task, Student) ou `@Getter/@Setter` (Event).
- **Relations** : éviter les `@OneToOne` bidirectionnels sans `@JsonIgnore` / `@JsonBackReference`
  → `StackOverflowError` à la sérialisation.
- **Ne jamais exposer** `password`, `passwordResetToken` dans une réponse JSON.
- Les packages racine diffèrent par service et **ne doivent pas être renommés** :
  `com.crm.authservice.*`, `org.crm.student.*`, `com.telusko.*`, `com.codingworld.*`,
  `com.crm.event_management_system.*`.

### Frontend

```
src/app/
├── components/    header, navbar, userdetails, candidate-form, assign-task-form, success
├── pages/         login, dashboard, candidates, candidate-detail, pipeline, task, event,
│                  contact, type, venue, user, calender, forgot-password, self-creationn,
│                  update-candidate, candidate-form-dialog
├── services/      un fichier par ressource backend, providedIn: 'root'
├── guards/        auth.guard.ts (CanActivateFn)
├── interceptors/  auth.interceptor.ts (HttpInterceptorFn) — ajoute le Bearer
├── pipes/         candidate-filter, fieldFilter, paginate
├── models/        interfaces TypeScript
└── app.routes.ts  routing
```

- **Standalone components** uniquement (Angular 18) — pas de `NgModule`.
- **Toutes les routes sont chargées paresseusement** via `loadComponent` dans
  `app.routes.ts`. Ne jamais réintroduire un `import` statique de composant de page
  : c'est ce qui faisait revenir le bundle initial à 6,81 Mo. Les composants ouverts par
  `MatDialog` (`SuccessComponent`, `AssignTaskFormComponent`,
  `CandidateFormDialogComponent`) restent des imports statiques **dans la page qui les ouvre**.
- Le choix d'afficher ou non le header/navbar se fait sur **l'URL** (`FULLSCREEN_ROUTES`
  dans `app.component.ts`), jamais sur le nom de la classe du composant : les noms manglés
  (`_LoginComponent`) n'existent qu'en build production et `route.component` vaut
  `undefined` pour une route `loadComponent`.
- **Providers** dans `app.config.ts` : `provideRouter`, `provideHttpClient(withInterceptors([...]))`,
  `provideAnimationsAsync()` (une seule fois).
- Les URLs doivent venir de `src/environments/environment.ts` (dev) et
  `environment.prod.ts` (prod), swapées par `fileReplacements` dans `angular.json`.
  **En prod, préférer des chemins relatifs** (`/api/...`) et laisser nginx router.
- `strict: true` + `strictTemplates: true` sont activés dans `tsconfig.json`.
- Les feuilles de style d'un composant ne doivent pas servir de cache à une librairie
  tierce : le thème Syncfusion (2 161 kB) vit dans `calender.component.scss` pour rester
  dans le chunk lazy du calendrier.
- `optimization.fonts` est désactivé en production : Inter est auto-hébergé via
  `@fontsource/inter`, donc le build ne doit dépendre d'aucun accès réseau.

---

## 6. Pipeline CI/CD (en cours de construction)

Objectif documenté dans `note.md` : chaîne CI/CD complète en 9 étapes
(conteneurisation → CI GitHub Actions → release → Heroku → Terraform/Azure →
Kubernetes kubeadm → manifests K8s → Helm chart → sécurité).

Organisation cible :

| Pipeline | Contenu |
|---|---|
| `.github/workflows/ci.yml` | lint · tests unitaires · tests d'intégration · non-régression · coverage |
| `.github/workflows/release.yml` | sur tag `v*` : build multi-arch → **GHCR + ACR miroir** + SBOM + Trivy |
| `infrastructure/` (à créer) | Terraform Azure : VMs, NSG, Storage, ACR |
| `deploy/kubernetes/` (à créer) | manifests + probes + StatefulSet + certificat TLS |
| `deploy/helm/crm/` (à créer) | chart + values + publication (GitHub Releases) |

Décisions actées :

- **Un seul** workflow CI, **jobs parallèles** + gate final.
- Tests d'intégration via **Testcontainers** (MySQL 8) + WireMock pour stubber Eureka.
- Non-régression : **contrat d'API** (snapshots JSON) + **schéma MySQL** validé.
- Coverage : **seuil progressif** à 30 %, trajectoire documentée.
- Front en CI : Karma **ChromeHeadlessNoSandbox** (`--no-sandbox --disable-gpu`) + `CHROME_BIN`.

---

## 7. Pièges connus — ne pas « corriger » à l'aveugle

### Bloquants (à corriger en priorité)

| Piège | Détail |
|---|---|
| Pas de `.dockerignore` | Le `COPY . .` du Dockerfile front copie `node_modules` et casse le `npm ci` propre de l'image. |
| `contextLoads()` réels | Les tests `@SpringBootTest` de student / task / notification / event démarrent JPA + Eureka + **connexion MySQL réelle** → échouent en CI. `api-gateway` et `service-discovery` ont de vrais tests de contrat. |
| `models/candidate.spec.ts` | Fait `new Candidate()` sur une **interface** → erreur de compilation TS2351 → `ng test` ne démarre pas. |
| Specs de services Angular | `TestBed.configureTestingModule({})` sans `provideHttpClient` → `NullInjectorError`. |
| `ng test` sans `--watch=false` | Le job CI ne se termine jamais. |
| `spring.profiles.active: dev` | Codé en dur dans `auth-service`, et **toutes** les propriétés critiques (`JWT_SECRET_KEY`, `activation-url`, `MAIL_*`) n'existent que dans `application-dev.yml`. Il n'y a pas d'`application-prod.yml`. |
| `JWT_SECRET_KEY` vide en compose | `${JWT_SECRET_KEY}` sans défaut → Docker injecte la chaîne vide, qui **écrase** le `${JWT_SECRET_KEY:change-me-in-env}` du YAML → `WeakKeyException` au 1er login. |
| `ACTIVATE_ACCOUNT` vs `activate_account` | L'enum `EmailTemplateName` fait `emailTemplate.name()` → cherche `ACTIVATE_ACCOUNT.html`, mais le fichier est `activate_account.html`. Casse sur un système de fichiers sensible à la casse (JAR sous Linux). |
| `file.upload-dir` mal indenté | Dans `auth-service/application.yml`, `file:` est sous `spring:` → la clé réelle est `spring.file.upload-dir`, alors que `@ConfigurationProperties(prefix = "file")` lit `file.upload-dir` → `uploadDir = null`. |
| 17 URLs `localhost` codées en dur | 9 dans les services Angular + 2 dans des templates HTML + 1 dans un composant + `proxy.conf.json`. Le navigateur du client appelle **son propre** localhost → les blocs `location /api/` de `nginx.conf` ne sont jamais atteints. |
| 7 URLs `localhost` en Java | Voir §2. Casse le déploiement conteneurisé. |

### Déjà corrigés (ne pas réintroduire)

| Piège | Correction | Commit |
|---|---|---|
| `"crm": "file:"` | dépendance supprimée du manifest et du lockfile | `48997d2` |
| `maven-compiler-plugin` en Java 7 | override `<source>7</source>` supprimé | `48997d2` |
| Lombok en scope `annotationProcessor` | passé à `optional`, comme les 6 autres services | `48997d2` |
| `service-discovery` en Spring Boot 2.6.11 | aligné sur 3.3.5 / Spring Cloud 2023.0.3, actuator déclaré explicitement | `48997d2` |
| Budgets Angular (1 Mo / 4 kB) | `initial` 7/8 Mo, `anyComponentStyle` 2500 kB/3 Mo. Le seuil de style est dominé par le thème Syncfusion : le resserrer suppose de remplacer Syncfusion, pas d'écrire des feuilles plus petites | `48997d2`, `4912fa5` |
| Bundle initial de 6,81 Mo | 20 routes en `loadComponent` + CSS Syncfusion déplacés dans le composant calendrier → **1,51 Mo** | `4912fa5` |
| Build dépendant du réseau | `optimization.fonts: false`, Inter auto-hébergé via `@fontsource/inter` | `4912fa5` |
| Layout conditionné aux noms de classes manglés | décision prise sur l'URL via `FULLSCREEN_ROUTES` | `4912fa5` |

### Applicatifs

- `DELETE /api/v1/auth/delete` et `DELETE /api/candidates/delete` : `@PathVariable` sans
  variable dans le chemin du mapping → erreur garantie à chaque appel.
- `GET /pipeline` (student-service) renvoie la vue Thymeleaf `"pipeline"` qui **n'existe pas**
  → 500 systématique.
- `Candidate ↔ ProfilePhoto` : `@OneToOne` bidirectionnel sans annotation Jackson →
  `GET /api/candidates/` provoque un `StackOverflowError`.
- `User.password` (hash BCrypt) est sérialisé dans `/all-users`, `/user-info`, `/email/{email}`.
- `TaskService.createTask()` : `save(task)` **puis** `setAssignedToEmail(...)` → la colonne
  reste `NULL` en base.
- `candidateId` généré par `countByCandidateIdStartingWith(prefix) + 1` → collisions en concurrence.
- `field.substring(0, 3)` → `StringIndexOutOfBoundsException` si `field` fait moins de 3 caractères.
- `DocumentService` écrit dans `uploads/` (relatif au `WORKDIR`) → **aucun volume monté**,
  les documents sont perdus à chaque recréation de conteneur.
- `@CrossOrigin(origins = "http://localhost:4200")` codé en dur sur les 4 contrôleurs
  d'`Event_Management_System` et sur `TaskController`.
- Typos dans les URLs d'e-mail : `avtivate-account`, `resertUrl` ; et
  `@Value("http://localhost:4200/resertUrl")` est un **littéral Java**, pas un placeholder.
- `auth.service.ts` (front) : `private router: any` jamais injecté → `logout()` lève un
  `TypeError`.
- `app.component.ts` appelle `clearTokenOnReload()` au `ngOnInit` → **recharge
  de page = déconnexion**.
- Doublons à supprimer — `candidate.service.ts` (port 8082, non injecté) vs
  `candidate-service.service.ts` (port 8060, réellement utilisé) ; `PaginatePipe` (mort) ;
  `provideAnimationsAsync()` listé deux fois dans `app.config.ts`.
- Secrets à ne jamais committer : compte admin `Admin@12345` (dans le `CommandLineRunner`
  de `AuthApi1Application`), clé de licence Syncfusion dans `src/main.ts`,
  `href="kamdemjordan57@gmail.com"` dans `candidate-notification.html`.

### Codes morts / dépendances inutiles

`StorageRepository` (vide) · `WebConfig` (100 % commenté) · `CustomResponse` ·
`ImageUtils` · `FileStorageConfig` · `EventDTO` · `WhatsAppNotificationRequest` ·
`Exceptions/TaskNotFoundException` dans le student-service · `okta-spring-boot-starter`
et `oauth2-*` dans `auth-service` · `KeycloakJwtAuthenticationConverter` (jamais branché) ·
`spring-boot-starter-data-jpa` dans `notification-service` (sans BDD).

---

## 8. Conventions de contribution

- **Branches** : `main` (protégée) + branches de fonctionnalité.
- Commits : **Conventional Commits** (`feat:`, `fix:`, `ci:`, `chore:`).
- Ne jamais committer de secret (`.env`, `*_rsa`, credentials). Le `.gitignore` couvre
  déjà `.env*` sauf `.env.example`.
- Toute modification de configuration Spring doit rester compatible Docker **et**
  Kubernetes : jamais de chemin absolu, toujours des variables d'environnement.
- Ajouter une route dans la gateway **et** le `nginx.conf` **et** la config Helm.
- Avant de conclure qu'un endpoint est mort, vérifier qu'il n'est appelé que par le front :
  les appels HTTP sont tous dans le Java, aucun tracing distribué n'est en place.
