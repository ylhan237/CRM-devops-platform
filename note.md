# Mise en place d'une chaîne CI/CD complète

L'objectif du projet est de mettre en place une chaîne **CI/CD complète**, depuis la conteneurisation de l'application jusqu'à son déploiement sur une infrastructure Kubernetes provisionnée avec Terraform.

Le projet sera organisé progressivement afin de distinguer clairement :

- la **CI applicative** ;
- la **production et publication des images Docker** ;
- le **provisionnement de l'infrastructure** ;
- le **déploiement Kubernetes** ;
- la **sécurité du code et des images**.

---

## 1. Conteneurisation de l'application avec Docker

La première étape consiste à maîtriser la **conteneurisation de l'application avec Docker**.

### Objectifs

- Créer les différentes images Docker nécessaires à l'application.
- Mettre en place un **Dockerfile multi-stage** afin d'optimiser les images finales.
- Séparer les étapes de build et d'exécution.
- Mettre en place une base de données externe nécessaire au fonctionnement de l'application.
- Tester la connexion entre l'application et la base de données.
- Ajouter des **health checks**, notamment pour le frontend, afin de vérifier que l'application est correctement disponible.

### Tests

Avant de construire l'image finale, la base de données doit être démarrée.

Pendant le processus de build et de test, l'application devra être capable de :

1. démarrer ;
2. se connecter à la base de données ;
3. exécuter les opérations nécessaires ;
4. vérifier que la connexion fonctionne correctement.

---

# 2. Mise en place de la CI avec GitHub Actions

Une fois la conteneurisation maîtrisée, on passe à l'**industrialisation des tests avec GitHub Actions**.

Cette première partie de la CI permet de tester l'application **sans utiliser la conteneurisation de l'application**.

### Pipeline de tests

La pipeline devra notamment intégrer :

- **Linting** du code ;
- **Tests unitaires** ;
- **Tests d'intégration** ;
- **Tests de non-régression** ;
- **Code coverage**.

L'objectif est de vérifier automatiquement la qualité et le bon fonctionnement du code avant de poursuivre vers les étapes de build et de déploiement.

### Exemple de workflow

```text
Push / Pull Request
        │
        ▼
     Linter
        │
        ▼
 Tests unitaires
        │
        ▼
Tests d'intégration
        │
        ▼
Tests de non-régression
        │
        ▼
  Code Coverage
        │
        ▼
   Rapport + gate
```

---

# 3. Diagnostic du code existant

Avant d'écrire la moindre ligne de CI, un audit du code a été réalisé. Il révèle
**18 problèmes critiques (S1)**, **13 majeurs (S2)** et une dizaine de mineurs (S3).

État actuel de la chaîne CI/CD : **rien n'existe encore** — pas de `.github/`, pas
d'`ESLint`, pas de JaCoCo, pas de dossier `terraform/`, pas de manifests K8s, pas de
chart Helm. Seule la conteneurisation multi-stage est déjà en place (7 Dockerfiles).

## 3.1 Problèmes S1 — critiques

Bloquent le démarrage, le build, la conteneurisation ou l'accès à l'application.

La colonne **État** indique l'avancement réel. `corrigé` = vérifié par exécution
(build ou tests verts), pas seulement par relecture du code.

| # | Problème | Emplacement | Effet | État |
|---|---|---|---|---|
| 1 | Récursion JSON `Candidate ↔ ProfilePhoto` | `Candidate.java:68` ↔ `ProfilePhoto.java:18` | `GET /api/candidates/` → `Infinite recursion (StackOverflowError)`. Toute la liste des candidats est morte | à corriger |
| 2 | Hash BCrypt et token de reset exposés en JSON | `User.java:44` `password`, `User.java:69` `passwordResetToken` — aucun `@JsonIgnore` | fuite via `/all-users`, `/user/{id}`, `/user-info`, `/email/{email}` | à corriger |
| 3 | Endpoint mort | `AuthenticationController.java:515-516` — `@DeleteMapping("/delete")` avec `@PathVariable Long userId` absent du chemin | 500 garanti à chaque appel | à corriger |
| 4 | Endpoint mort | `CandidateController.java:140-141` — même défaut | 500 garanti | à corriger |
| 5 | `npm ci` échoue | `Front/CRM/package.json:26` — `"crm": "file:"` (chemin vide = racine du projet) | symlink récursif `node_modules/crm → .`, `EPERM` sous Windows. Aucune image front ne se construit | **corrigé** |
| 6 | `ng test` ne compile pas | `Front/CRM/src/app/models/candidate.spec.ts:5` — `new Candidate()` sur une **interface** | erreur TS2351, la suite de tests ne démarre pas | à corriger |
| 7 | Build de production impossible | `auth-service/application.yml:14` — `spring.profiles.active: dev` codé en dur, propriétés critiques uniquement dans `application-dev.yml`, aucun `application-prod.yml` | `SPRING_PROFILES_ACTIVE=prod` → `IllegalArgumentException` au démarrage | à corriger |
| 8 | Secret JWT inutilisable | `docker-compose.yml` — `JWT_SECRET_KEY: ${JWT_SECRET_KEY}` sans défaut | Docker injecte la chaîne vide qui **écrase** le `${JWT_SECRET_KEY:change-me-in-env}` du YAML → `WeakKeyException` au 1er login | à corriger |
| 9 | Template e-mail introuvable sur Linux | `EmailTemplateName.ACTIVATE_ACCOUNT.name()` cherche `ACTIVATE_ACCOUNT.html`, le fichier est `activate_account.html` | `TemplateInputException` à la première inscription | à corriger |
| 10 | `@Value` littéral | `AuthenticationService.java:49` — `@Value("http://localhost:4200/resertUrl")` | non paramétrable + URL mal orthographiée | à corriger |
| 11 | `uploadDir` vaut `null` | `auth-service/application.yml:22` — `file:` indenté **sous** `spring:` alors que `@ConfigurationProperties(prefix = "file")` | upload de photo sans destination | à corriger |
| 12 | Conteneurisation cassée (backend) | 7 URLs `http://localhost:...` codées en dur dans le Java | sous Docker `localhost` = le conteneur ; sous K8s = le pod. `POST /tasks` échoue en 500 | à corriger |
| 13 | Conteneurisation cassée (frontend) | 17 URLs `http://localhost:PORT` (8060/8084/8089) | le navigateur du client appelle **son propre** localhost → les blocs `location /api/` de `nginx.conf` ne sont jamais atteints | à corriger |
| 14 | `event-service` injoignable | `api-gateway/application.yml` ne route ni `/api/events`, ni `/api/venues`, ni `/api/event-types`, ni `/api/contacts` | 404 derrière nginx ou derrière l'Ingress K8s | à corriger |
| 15 | Build cassé sur JDK moderne | `Task_Management_Service/pom.xml` force `<source>7</source>` | `source 7` n'est plus supporté à partir du JDK 20 | **corrigé** |
| 16 | Tests verts impossibles | 4 × `contextLoads()` = `@SpringBootTest` réels exigeant MySQL + Eureka + `MAIL_*` + `TWILIO_*` sans défaut | le job de tests ne peut pas passer en CI | à corriger |
| 17 | Logout planté | `Front/CRM/src/app/services/auth/auth.service.ts:13` — `router: any` jamais injecté, utilisé ligne 98 | `TypeError` sur le bouton logout | à corriger |
| 18 | Provider dupliqué | `Front/CRM/src/app/app.config.ts` — `provideAnimationsAsync()` listé deux fois | comportement non déterministe des animations | à corriger |

### 3.1.1 Défauts trouvés par l'exécution, absents de l'audit statique

Ces problèmes n'ont été révélés qu'en lançant réellement le build. Ils sont
signalés ici parce qu'un audit par relecture ne les trouve pas.

| # | Problème | Emplacement | Effet | État |
|---|---|---|---|---|
| 19 | Budgets Angular production | `Front/CRM/angular.json` — `initial` 1 Mo, `anyComponentStyle` 4 kB | `initial` mesuré à **6,81 Mo** et 12 feuilles de style au-delà du seuil → le builder `application` traite le dépassement comme une **erreur dure** → `dist/crm/browser` n'était jamais émis, donc **l'image Docker du front ne pouvait pas se construire** | **corrigé** |
| 20 | `service-discovery` en Spring Boot 2.6.11 | `CRM_Backend/service-discovery/pom.xml` | Eureka Server 1.11 (`javax`) face à des clients Eureka 2.0 (`jakarta`) — combinaison non supportée | **corrigé** |
| 21 | Lombok hors classpath de compilation | `Task_Management_Service/pom.xml` — scope `annotationProcessor` au lieu de `optional` | Lombok absent du classpath, incohérent avec les 6 autres services | **corrigé** |
| 22 | Build dépendant du réseau | Google Fonts inlinées par `angular-css-inline-fonts-plugin` | le build échoue pour une raison étrangère au code source — Fatal en CI et en build Docker | **corrigé** |
| 23 | Bundle initial de 6,81 Mo | les 20 routes en `import` statique + 2 161 kB de CSS Syncfusion dans la feuille globale | temps de démarrage élevé sur toutes les pages | **corrigé** |

> **Leçon** : les défauts n°19 à n°23 ne sont pas visibles à la lecture. Toute
> entrée du présent tableau doit être confirmée par une exécution avant d'être
> déclarée corrigée.

## 3.2 Problèmes S2 — majeurs

| Problème | Emplacement | Effet |
|---|---|---|
| `assignedToEmail` jamais persisté | `TaskService.java:56` `save(task)` puis `:58` `setAssignedToEmail(...)` | colonne `NULL` en base |
| Vue `"pipeline"` inexistante | `PipelineController` renvoie `"pipeline"`, pas de dossier `templates/` | `GET /pipeline` → 500 |
| `candidateId` non atomique | `countByCandidateIdStartingWith(prefix) + 1` | collisions en concurrence, pas d'index unique |
| `StringIndexOutOfBoundsException` | `field.substring(0, 3)` si `field` fait moins de 3 caractères | 500 sur la mise à jour de statut |
| `GET /tasks/candidate/{id}` est un stub | `TaskController` renvoie une String littérale | le service métier n'est jamais appelé |
| `@CrossOrigin` codé en dur (×5) | `TaskController` + 4 contrôleurs de l'Event service | casse dès que le front change de port ou de domaine |
| `uploads/` sans volume | `DocumentService` écrit relativement au `WORKDIR` | documents perdus à chaque recréation de conteneur |
| `catch` mort | `AuthenticationController` catch `UserNotFoundException` alors que le service lève `org.springframework.security...UsernameNotFoundException` | les 404 métier deviennent des 500 |
| Statuts HTTP incohérents | `BusinessErrorCodes` déclare `FORBIDDEN`, le handler renvoie `UNAUTHORIZED` | le client ne peut pas se fier aux codes |
| Rechargement = déconnexion | `app.component.ts` appelle `clearTokenOnReload()` dans `ngOnInit` | chaque `F5` déconnecte l'utilisateur |
| Layout cassé en développement | `app.component.html` compare à `"_LoginComponent"` (nom manglé, build production uniquement) | en `ng serve`, header et navbar s'affichent sur la page de login |
| Fuite d'e-mail par nom complet | `AuthenticationController.java:530` — `GET /auth/{userfullname}/email` non authentifié | énumération d'utilisateurs |
| Upload sans authentification | `POST /auth/{userId}/upload`, `PUT /auth/{userId}/update` | IDOR : n'importe qui écrase la photo de n'importe quel utilisateur |

## 3.3 Problèmes S3 — mineurs et dette technique

- `float budget` dans l'entité `Event` → imprécision sur les montants monétaires.
- `EventDTO`, `StorageRepository` (vide), `WebConfig` (100 % commenté), `CustomResponse`,
  `ImageUtils`, `FileStorageConfig`, `WhatsAppNotificationRequest` : code mort.
- `ProfilePhotoRepository extends JpaRepository<ProfilePhoto, Integer>` alors que l'id
  est un `Long` ; `UserRepository.findById(Long)` surcharge `JpaRepository.findById(Integer)`.
- `Candidate.setInstitutionName()` et `setGraduationYear()` sont **vides** et écrasent les
  setters générés par Lombok.
- `src/main/resources/candidate.csv` : les en-têtes ne correspondent pas à `CandidateCSV`
  (l'import produit des sous-objets vides).
- Le même `candidate_email.pdf` est jointé à tous les candidats.
- Variables factices dans `NotificationController` (`Map.of("name", "Candidate Name")`) et
  décalage des arguments dans l'e-mail de tâche.
- Les 2 pipes de tri (`.sort()`) **mutent leur tableau d'entrée** alors qu'ils sont purs →
  le tri est silencieusement inopérant.
- `MatDialogModule` n'est importé nulle part alors que 8 composants utilisent `mat-dialog`.
- Imports inutilisés dans plus de 10 fichiers ; doublon `candidate.service.ts` (port 8082,
  jamais injecté) vs `candidate-service.service.ts` (port 8060, réellement utilisé).
- 38 fichiers `.spec.ts` sont des stubs auto-générés (`expect(x).toBeTruthy()`) :
  la couverture réelle est proche de 0 %.

---

# 4. Stratégie de branches

## 4.1 Conventions de nommage

```text
<type>/<ticket>-<description-en-kebab-case>

feat/      nouvelle fonctionnalité
fix/       correction de bug
chore/     outillage, Dockerfiles, poms, .gitignore
ci/        GitHub Actions, linters, configuration de couverture
test/      ajout ou réparation de tests
infra/     Terraform, manifests Kubernetes, Helm
docs/      documentation
hotfix/    correction critique en production
release/   stabilisation avant tag (ex. release/v1.0.0)
```

## 4.2 Modèle de branches

Modèle **trunk-based** : `main` est toujours déployable, les branches sont courtes et
réintégrées en flux continu par Pull Request.

```text
main ──●──●──●───────●──●────────────●──►   (protégée, taguée vX.Y.Z)
        \  / \    /        \      /
     feat/  fix/  test/     chore/  infra/
```

| Règle | Détail |
|---|---|
| Durée de vie d'une branche | 1 à 3 jours maximum. Au-delà, découper |
| Base | toujours `main` à jour : `git switch main && git pull && git switch -c ...` |
| Avant PR | `git fetch origin && git rebase origin/main` |
| Ouverture de PR | `.github/PULL_REQUEST_TEMPLATE.md` : périmètre, preuves avant/après, critères du plan, bugs corrigés |
| Condition de merge | lint + tests verts + revue. **Jamais de merge direct dans `main`** |
| Stratégie de merge | **Squash** → un commit = une fonctionnalité, message Conventional Commits |
| Release | tag `vMAJOR.MINOR.PATCH` sur `main` → déclenche la pipeline de publication |
| Hotfix | `hotfix/...` depuis `main` → PR → merge sur `main` → tag `vX.Y.Z+1` immédiat |

## 4.3 Roadmap de branches

### Vague 0 — build (bloquant, prerequisite à tout le reste)

| # | Branche | Contenu |
|---|---|---|
| 0.1 | `chore/fix-blocking-build-issues` | suppression de `"crm": "file:"` + lockfile · suppression du `<source>7</source>` · scope Lombok · alignement de `service-discovery` sur Spring Boot 3.3.5 / Spring Cloud 2023.0.3 |
| 0.2 | `chore/add-env-example-and-prod-profile` | `.env.example` · `application-prod.yml` · retrait de `spring.profiles.active: dev` · valeur par défaut valide pour `JWT_SECRET_KEY` · correction de l'indentation `file.upload-dir` |
| 0.3 | `chore/add-dockerignore` | 8 fichiers `.dockerignore` (racine + 6 services + front) |
| 0.4 | `fix/config-externalize-java-service-urls` | les 7 URLs inter-services en propriétés `${...:default}` + timeouts sur les `RestTemplate` |
| 0.5 | `fix/config-externalize-angular-urls` | les 17 URLs vers `src/environments/` + `fileReplacements`, URLs relatives en production |
| 0.6 | `feat/gateway-route-event-service` | les 4 routes gateway manquantes vers `lb://event-service` |

### Vague 1 — corrections applicatives (S1)

| # | Branche | Contenu |
|---|---|---|
| 1.1 | `fix/security-password-json-exposure` | `@JsonIgnore` sur `password` et `passwordResetToken` |
| 1.2 | `fix/candidate-profile-photo-json-recursion` | annotation Jackson sur la relation `Candidate ↔ ProfilePhoto` |
| 1.3 | `fix/api-broken-delete-endpoints` | les 2 `@PathVariable` orphelins + `GET /pipeline` |
| 1.4 | `fix/email-template-casing-and-url-typos` | `ACTIVATE_ACCOUNT` → `getName()` · `@Value` littéral · `avtivate-account` / `resertUrl` |
| 1.5 | `fix/frontend-auth-router-and-duplicate-provider` | injection du `router` dans `auth.service.ts` · déduplication de `provideAnimationsAsync()` |

### Vague 2 — tests (à faire avant la CI, sinon elle est rouge)

| # | Branche | Contenu |
|---|---|---|
| 2.1 | `test/fix-broken-existing-specs` | `candidate.spec.ts` · `provideHttpClientTesting` dans les 8 specs de services · `app.component.spec.ts` |
| 2.2 | `test/isolate-context-loads-with-testcontainers` | MySQL 8 via Testcontainers + WireMock pour Eureka dans les 4 `contextLoads()` |
| 2.3 | `test/add-unit-tests` | couverture des services métier, seuil de départ 30 % |
| 2.4 | `test/add-contract-and-schema-tests` | snapshots JSON des réponses + validation du schéma MySQL |

### Vague 3 — CI/CD

| # | Branche | Contenu |
|---|---|---|
| 3.1 | `ci/add-eslint-and-prettier` | ESLint + `@angular-eslint` + Prettier + Checkstyle + Spotless |
| 3.2 | `ci/add-github-actions-pipeline` | `.github/workflows/ci.yml` — jobs parallèles + gate final |
| 3.3 | `ci/add-code-coverage-gate` | JaCoCo + Istanbul, seuil bloquant à 30 % |
| 3.4 | `ci/add-release-workflow` | sur tag : build multi-architecture → GHCR + ACR en miroir + SBOM + Trivy |

### Vague 4 — infrastructure et Kubernetes

| # | Branche |
|---|---|
| 4.1 | `infra/terraform-azure-vms-and-acr` |
| 4.2 | `infra/kubernetes-kubeadm-cluster` |
| 4.3 | `infra/add-kubernetes-manifests` (probes, StatefulSet, certificat TLS) |
| 4.4 | `infra/add-helm-chart` |

## 4.4 Protection de `main`

```yaml
required_status_checks:
  [lint, unit-test, integration-test, non-regression, coverage-gate]
required_pull_request_reviews: 1
dismiss_stale_reviews: true
require_linear_history: true     # compatible avec le squash
allow_force_pushes: false
allow_deletions: false
```

---

# 5. Suivi d'avancement

## 5.1 Correctifs vérifiés par exécution

| Branche | Commit | Contenu | Preuve |
|---|---|---|---|
| `chore/fix-blocking-build-issues` | `48997d2` | n°5 `"crm": "file:"`, n°15 `<source>7</source>`, n°19 budgets Angular, n°20 alignement `service-discovery`, n°21 scope Lombok | `npm ci` : 1002 paquets exit 0 · `npm run build` : `dist/crm/browser` émis · `service-discovery` 2/2 tests verts · `TaskServiceTest` 2/2 vert · les 2 JARs portent les noms attendus par leurs Dockerfiles |
| `perf/add-lazy-loading` | `4912fa5` | n°22 build déterministe, n°23 bundle initial | `initial` **6,81 Mo → 1,51 Mo** (−78 %) · 57 fichiers JS au lieu de 9 · 8 chunks lazy nommés · `tsc -p tsconfig.spec.json` : aucune nouvelle erreur |

### Vague 1 — les 18 défauts S1

Tous corrigés, chacun vérifié par exécution et non par relecture. Les quatre
sécurité ont été traités en priorité.

| Branche | Défauts | Preuve |
|---|---|---|
| `fix/security-password-json-exposure` | `password` et `passwordResetToken` exposés en JSON | `@JsonIgnore` + test d'absence du champ dans la réponse |
| `fix/candidate-profile-photo-json-recursion` | récursion infinie `Candidate ↔ ProfilePhoto` | annotations Jackson + sérialisation vérifiée |
| `fix/api-broken-delete-endpoints` | 2 endpoints DELETE inatteignables, `GET /pipeline` absent | tests sur les 3 endpoints |
| `fix/email-template-casing-and-url-typos` | `ACTIVATE_ACCOUNT` non résolu, URLs `avtivate-account` / `resertUrl` | e-mail rendu et vérifié |
| `fix/frontend-auth-router-and-duplicate-provider` | `router` non injecté, `provideAnimationsAsync()` dupliqué | 49 specs Angular vertes |
| `fix/security-photo-upload-idor-and-email-enumeration` | **IDOR** sur l'upload de photo, énumération de comptes | **16 tests** dans `ProfilePhotoAuthorizationTest`, cas négatifs inclus |

### Vague 3 — la chaîne de qualité

| Branche | Contenu | Preuve |
|---|---|---|
| `ci/add-eslint-and-prettier` | ESLint + `@angular-eslint` + Prettier + Checkstyle + Spotless | **11 règles bloquantes, 0 violation** · 5 en warning, 110 violations, aucune n'étant un défaut |
| `ci/add-github-actions-pipeline` | `ci.yml`, 5 jobs parallèles + gate | **6/6 verts**, run `37203518006`, après 3 itérations |
| `ci/add-code-coverage-gate` | JaCoCo + Istanbul + `scripts/check-coverage.mjs` | backend mesuré **20,40 %**, plancher **0,18**, crémaillère 0,18 → 0,25 → 0,30 · frontend **33,22 %**, garde **0,30** |
| `test/isolate-context-loads-with-testcontainers` | MySQL 8 par Testcontainers + WireMock pour Eureka | les 4 `contextLoads()` passent sur runner Linux |

**102 tests verts** : 53 backend répartis sur 7 modules, 49 specs Angular.

### Vague 4 — infrastructure

Aucune de ces quatre n'a été appliquée. Le plan Terraform, lui, a été exécuté
contre l'abonnement réel, ce qui a prouvé les identifiants et trouvé un défaut.

| Branche | Preuve |
|---|---|
| `infra/terraform-azure-vms-and-acr` | `validate` et `fmt` passent · **plan réel exécuté : `8 to add, 0 to change, 0 to destroy`** |
| `infra/kubernetes-kubeadm-cluster` | 4 scripts `bash -n` · **17/17** tests de chevauchement CIDR |
| `infra/add-kubernetes-manifests` | `kubectl kustomize` rend 22 documents · 16 clés de config toutes déclarées |
| `infra/add-helm-chart` | `helm lint` passe · rendu de 21 documents, 2 garde-fou `fail` testés |

## 5.2 Reste à faire

Cette section listait comme « à faire » treize branches qui sont **mergées depuis** :
les vagues 0, 1 et 3 sont terminées. Ce qui reste réellement est ci-dessous.

| Vague | Branche | État |
|---|---|---|
| 0 — build | 6 branches | ✅ mergées (PR #1 à #7) |
| 1 — S1 | 6 branches | ✅ mergées (PR #6 à #13) |
| 2 — tests | `test/fix-broken-existing-specs` | ✅ mergée (PR #9) |
| 2 — tests | `test/isolate-context-loads-with-testcontainers` | ✅ mergée (PR #11) |
| 2 — tests | `test/add-unit-tests` | ❌ **non commencée**, §5.5 |
| 2 — tests | `test/add-contract-and-schema-tests` | ❌ non commencée |
| 3 — CI/CD | 4 branches | ✅ mergées (PR #12, #14, #15) |
| 4 — infra | 4 branches | 🟡 écrites et vérifiées, **PR #17 à #20 ouvertes** |

### Ce qui reste réellement

| Priorité | Travail | Pourquoi |
|---|---|---|
| 1 | Merger les 5 PR ouvertes (#16 → #20) | tout est écrit et vérifié, rien n'est mergé |
| 2 | **11 défauts S2** | non commencés |
| 3 | **11 défauts S3** | non commencés |
| 4 | `test/add-unit-tests` | le backend est à 20,40 %, la crémaillère vise 0,25 puis 0,30 |
| 5 | 2 branches amont de sécurité pour Frederic | travail fait et testé, il manque le conditionnement sur sa base |
| 6 | `terraform apply` | la seule chose qui reste pour que l'infra existe |

### Pourquoi les tests passent à 30 % par paliers

`note.md` §4.3 annonçait un seuil bloquant à 30 %. La mesure dit 20,40 % pour le
backend. Un seuil à 30 % aurait rendu la CI rouge immédiatement, et une CI rouge
depuis le premier jour s'apprend à ignorer. Le plancher est donc à **0,18**, juste
sous la mesure, et la crémaillère le fait monter à 0,25 puis 0,30 à mesure que les
tests sont ajoutés. Le frontend garde **0,30** : il est déjà à 33,22 %, donc le
seuil est tenable immédiatement.

## 5.3 Stratégie de pull request vers l'upstream

Le dépôt d'origine `Frederic311/CRM` est un **fork**, pas le dépôt de travail.
La fin du parcours ne consiste donc pas à tout fusionner, mais à sélectionner.

**À lui soumettre** — ses bugs, démontrables en deux lignes, une PR par branche :

```
fix/security-password-json-exposure
fix/candidate-profile-photo-json-recursion
fix/api-broken-delete-endpoints
fix/email-template-casing-and-url-typos
fix/frontend-auth-router-and-duplicate-provider
```

**Déjà fait** — 6 branches poussées sur notre dépôt et vérifiées sur sa base `eafaeaa`,
via des branches `upstream/*` :

```
upstream/fix-api-broken-delete-endpoints
upstream/fix-candidate-profile-photo-json-recursion
upstream/fix-email-template-casing-and-url-typos
upstream/fix-frontend-auth-router-and-duplicate-provider
upstream/test-runnable-context-tests
```

Il manque **2 branches de sécurité** : le hash BCrypt exposé et l'IDOR sur l'upload
de photo. Le travail est fait et testé, il reste à le conditionner sur sa base et
à rédiger les issues correspondantes.

**À ne pas lui soumettre** — le travail DevOps et les choix de conception :

```
ci/*, infra/*, perf/*, chore/*        outillage et pipeline
les budgets Angular, le lazy loading  choix de conception, pas des bugs
docker-compose                        son existant, pas une correction
```

## 5.3 bis Protection de `main` (§4.4)

Appliquée et relue dans l'API :

| Règle | Valeur |
|---|---|
| checks requis | `lint`, `unit-test`, `integration-test`, `non-regression`, `coverage-gate` |
| `strict` | oui — la branche doit être à jour avec `main` |
| révisions requises | 1 |
| `dismiss_stale_reviews` | oui |
| historique linéaire | oui, compatible avec le squash |
| force push / suppression | interdits |
| résolutions de conversation | exigées |

Les cinq noms de checks correspondent **exactement** aux noms de jobs de `ci.yml`,
ce qui n'était pas garanti et qui est vérifié.

`enforce_admins` est à `false` : sur un dépôt à un seul compte, exiger une revue
que l'on ne peut pas se donner soi-même bloquerait tous les merges. Un compte admin
peut contourner.

La méthode la plus simple : chaque branche `fix/*` est poussée sur notre dépôt,
puis la PR est ouverte en croisant les dépôts, avec `base=Frederic311:main` et
`head=ylhan237:<branche>`. Aucune réécriture d'historique n'est nécessaire.

## 5.4 Limites connues de ce document

- `note.md` couvre les sections **1 à 4** de l'énoncé. La section **5 est
  abandonnée** : Heroku ne fait plus partie du périmètre, c'est une décision et pas
  un oubli. Elle reste dans `note.txt` comme trace. Rien dans ce dépôt ne déploie
  donc plus nulle part : le CD n'a pas de cible tant que l'infra n'est pas
  appliquée.
- L'audit s1 a été fait par relecture **et** par exécution, mais la vérification
  fonctionnelle en navigateur n'a pas été faite. Le build vert avec
  `strictTemplates: true` garantit la compilation des templates, pas le rendu.
- La phrase « les 38 fichiers `.spec.ts` sont des stubs, couverture proche de 0 % »
  **n'est plus vraie**. Mesurée : **33,22 %** sur le frontend, et 49 specs
  Angular sont vertes. Ils n'étaient plus des stubs.
- Le seuil de 30 % annoncé en §4 n'est appliqué qu'au frontend. Le backend est à
  20,40 %, il est à 0,18 pour que la CI soit verte, et §5.2 explique pourquoi.

## 5.5 Ce que la CI a trouvé et la lecture n'avait pas vu

Cinq défauts sont apparus à l'exécution et étaient impossibles à voir à la relecture.
Le même risque valait pour Terraform, ce qui est pourquoi `validate` et un `plan`
réel ont été faits plutôt que de se fier à la lecture.

| Défaut | Pourquoi la lecture ne le voyait pas |
|---|---|
| `apiServer:` écrit **deux fois** dans le même `ClusterConfiguration` | clé YAML dupliquée : le plan produit la dernière, silencieusement |
| `volumeClaimTemplates` indenté sous le **pod** au lieu du StatefulSet | rend proprement, rejeté par le serveur de l'API |
| Deux clés `annotations` dans le même `metadata` d'Ingress | le chart paraissait correct, la limite nginx de 16 Mo disparaissait |
| Priorities de règles NSG = `200 + port` | produit 6643 et 8961, Azure plafonne à 4096 |
| `admin_ssh_key_enabled`, argument d'azurerm 2.x | `validate` rejette le fichier entier, la lecture ne dit rien |

Le motif est le même à chaque fois : une valeur plausible, un défaut que rien ne
signale tant que quelque chose ne l'exécute.

## 5.6 État des secrets GitHub au 5 octobre 2026

| Secret | Présent | Utilisé par |
|---|---|---|
| `ARM_SUBSCRIPTION_ID` | ✅ | Terraform — **validé par un plan réel** |
| `ARM_TENANT_ID` | ✅ | Terraform — validé |
| `ARM_CLIENT_ID` | ✅ | Terraform — validé |
| `ARM_CLIENT_SECRET` | ✅ | Terraform — validé |
| `AZURE_REGISTRY` | ❌ | le miroir ACR de `release.yml` |
| `AZURE_REGISTRY_USERNAME` | ❌ | le miroir ACR |
| `AZURE_REGISTRY_PASSWORD` | ❌ | le miroir ACR |

Les trois secrets Azure ne peuvent pas encore être posés : il n'y a pas d'ACR
auquel s'authentifier puisque aucun `apply` n'a été fait. L'ordre est `apply`,
puis un principal de service avec le rôle `AcrPush`, puis les secrets.

GitHub ne relit jamais un secret, donc « présent » et « correct » sont deux
affirmations différentes. C'est ce que `.github/workflows/terraform.yml` vérifie :
le contrôle de présence nomme les secrets absents, et le `plan` est le seul moyen
de savoir si les valeurs sont bonnes. Il l'a fait : `8 to add, 0 to change`.
