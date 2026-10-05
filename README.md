# Customer Relation System

Application CRM de gestion de candidatures et de suivi d'admission, en
architecture microservices, livrée par une chaîne CI/CD complète et déployée sur
Kubernetes via Terraform.

![MySQL](https://img.shields.io/badge/mysql-4479A1.svg?style=for-the-badge&logo=mysql&logoColor=white)
![Angular](https://img.shields.io/badge/angular-DD0031.svg?style=for-the-badge&logo=angular&logoColor=white)
![Java](https://img.shields.io/badge/java-007396.svg?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/spring_boot-6DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)
![Docker](https://img.shields.io/badge/docker-2496ED.svg?style=for-the-badge&logo=docker&logoColor=white)
![Terraform](https://img.shields.io/badge/terraform-844FBA.svg?style=for-the-badge&logo=terraform&logoColor=white)
![Kubernetes](https://img.shields.io/badge/kubernetes-326CE5.svg?style=for-the-badge&logo=kubernetes&logoColor=white)
![Helm](https://img.shields.io/badge/helm-0F1689.svg?style=for-the-badge&logo=helm&logoColor=white)

## Les trois pipelines

L'énoncé demande trois pipelines. Ils existent tous les trois.

| Pipeline | Workflow | Déclencheur |
|---|---|---|
| **Images** | `.github/workflows/release.yml` | tag `v*` ou manuel |
| **Infrastructure** | `.github/workflows/terraform.yml` | manuel, avec approbation |
| **Kubernetes** | les manifestes et le chart sont versionnés | via l'infrastructure |

La qualité est un quatrième workflow, `.github/workflows/ci.yml`, sur cinq jobs
parallèles et une porte finale.

## Architecture

Sept microservices Spring Boot derrière une gateway, un registre Eureka, une base
MySQL et un bundle Angular servi par nginx.

| Service | Port | Rôle |
|---|---|---|
| `api-gateway` | 8060 | **unique point d'entrée** du backend |
| `auth-service` | 8080 | authentification, JWT, photos de profil |
| `service-discovery` | 8761 | registre Eureka |
| `Student_Management_Service` | 8082 | candidats, documents, photos |
| `Task_Management_Service` | 8084 | tâches et affectations |
| `Notification_Service` | 8085 | email, SMS, WhatsApp |
| `Event_Management_System` | 8089 | événements, salles, contacts |
| MySQL | 3306 | `authdb`, `candidate`, `task`, `event_system` |
| `frontend` | 4200 en local, 80 dans l'image | bundle Angular |

```
Front/CRM  ──▶  api-gateway  ──▶  auth / student / task / notification / event
                                  (tous s'enregistrent dans service-discovery)
                                        │
                                        ▼
                                      MySQL
```

## Structure

| Chemin | Contenu |
|---|---|
| `CRM_Backend/` | les 7 microservices Spring Boot + le parent `build-tools` |
| `Front/CRM/` | le frontend Angular |
| `infra/terraform-azure-vms-and-acr/` | registre ACR et machine virtuelle |
| `infra/kubernetes-manifests/` | les manifestes bruts, via `kubectl apply -k` |
| `infra/helm-chart/` | le même déploiement, paramétré, via Helm |
| `infra/kubernetes-kubeadm-cluster/` | scripts de création du cluster |
| `scripts/check-coverage.mjs` | contrôle des seuils de couverture |
| `uploads/` | fichiers téléversés |

## Démarrage local

### Backend

Les services ont besoin les uns des autres, donc `docker-compose` est le chemin le
court. Il faut un `.env`, copié depuis `.env.example` et complété — les variables
sont vérifiées au démarrage et le compose refuse de partir sans.

```bash
cp .env.example .env      # puis compléter JWT_SECRET_KEY, DB_PASSWORD, MAIL_*
docker compose up -d mysql service-discovery
docker compose up -d
```

### Frontend

```bash
cd Front/CRM
npm ci
npm start
```

`npm ci` et non `npm install` : le lockfile est la référence, et `npm ci` échoue
quand il désaccorde avec `package.json`, ce qui est précisément le contrôle utile.

### Un service seul, en développement

```bash
cd CRM_Backend/auth-service
./mvnw spring-boot:run
```

Sous Windows : `.\mvnw.cmd spring-boot:run`

## Tests et couverture

**102 tests verts** : 53 côté backend sur 7 modules, 49 specs Angular.

```bash
cd CRM_Backend/auth-service && ./mvnw test     # un service
cd Front/CRM && npm test                       # le frontend
```

La couverture est **mesurée**, pas estimée, et le seuil est bloquant en CI :

| Partie | Mesure | Seuil |
|---|---|---|
| backend | **20,40 %** | plancher **0,18**, puis crémaillère 0,25 puis 0,30 |
| frontend | **33,22 %** | **0,30** |

Le backend n'est pas au 30 % demandé. Un seuil à 30 % rendait la CI rouge dès le
premier jour, et une CI rouge en permanence est une CI à laquelle on apprend à ne
pas regarder. Le plancher démarre donc juste sous la mesure et monte à mesure que
les tests arrivent.

## Qualité

- **Checkstyle** : 11 règles bloquantes, **0 violation** ; 5 règles en warning qui
  totalisent 110 signalements, aucun n'étant un défaut de code.
- **ESLint + Prettier** : ESLint bloque sur les erreurs ; Prettier est rapporté
  et non appliqué, le code n'ayant jamais été passé au format.
- **`scripts/check-coverage.mjs`** : lit les rapports JaCoCo et Istanbul et
  compare aux planchers.

## Docker

Un `Dockerfile` multi-étapes par microservice, plus un pour le frontend.

**Le contexte de build du backend est `CRM_Backend/`, pas le dossier du module.**
Les modules héritent d'un POM parent `build-tools` situé un niveau au-dessus, et un
contexte restreint au module ne peut pas l'atteindre. C'est la seule modification
nécessaire pour que les builds Docker fonctionnent, et elle a concerné
`docker-compose.yml`, le workflow de release et le `.dockerignore`.

```bash
docker compose build
docker compose up
```

## Infrastructure

Les trois étapes sont versionnées séparément et dans cet ordre.

### 1. Terraform — registre et machine

```bash
cd infra/terraform-azure-vms-and-acr
```

Crée un Azure Container Registry, que la release utilise comme **miroir**, et une
machine virtuelle portant Docker, Java 17, Node 20 et kubeadm.

**Rien n'est encore appliqué.** Le plan a été exécuté contre l'abonnement réel et
produit `9 to add, 0 to change, 0 to destroy`.

➡️ **[APPLY.md](infra/terraform-azure-vms-and-acr/APPLY.md)** pour la marche à
suivre, le coût, et ce qui se passe si ça échoue.

⚠️ La machine coûte **30 à 40 € par mois**, à la minute. Le disque facture même
arrêtée.

### 2. Cluster kubeadm

```bash
cd infra/kubernetes-kubeadm-cluster
export CRM_PUBLIC_IP=<ton IP publique>
sudo ./init-control-plane.sh
```

Le script refuse de démarrer sur une machine mal préparée, parce qu'un `kubeadm
init` qui échoue aux deux tiers laisse un état à démonter à la main. Il vérifie le
driver de cgroup de containerd, l'absence de swap, et que le réseau des pods ne
recouvre pas le VNet — un recouvrement qui ne échoue pas bruyamment.

`join-worker.sh` permet d'ajouter un nœud.

### 3. Manifestes ou chart

Les deux décrivent la même application. Les manifestes sont la référence : ils se
lisent de bout en bout. Le chart est ce qu'on installe deux fois.

```bash
kubectl apply -k infra/kubernetes-manifests/
```

```bash
helm install crm infra/helm-chart -n crm
```

MySQL est un **StatefulSet**, parce qu'un Deployment peut remplacer son pod et
revenir avec un volume vide. Les sondes sont **TCP** et non HTTP : seul
`service-discovery` porte l'actuator Spring, donc `/actuator/health` répond là et
nulle part ailleurs, et une sonde HTTP ailleurs échouerait pour une raison qui n'a
rien à voir avec la santé du service.

## CI

`main` est protégée. Une pull request doit :

- passer `lint`, `unit-test`, `integration-test`, `non-regression`, `coverage-gate`
- recevoir **1 approbation**
- être à jour avec `main`, en squash

Le nom des cinq checks correspond exactement à celui des jobs de `ci.yml`, ce qui
n'était pas garanti.

## Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Construit 8 images **multi-architectures** (`linux/amd64` et `linux/arm64`), les
pousse dans GHCR, génère un SBOM, les scanne avec Trivy, et les **reflète** vers
Azure Container Registry si les trois secrets `AZURE_REGISTRY*` sont posés.

Le dernier run complet a donné **26 jobs verts sur 26**, avec 8 images publiées et
leurs digests.

## Secrets nécessaires

| Secret | Utilisé par |
|---|---|
| `ARM_SUBSCRIPTION_ID`, `ARM_TENANT_ID`, `ARM_CLIENT_ID`, `ARM_CLIENT_SECRET` | Terraform — **vérifiés** par un plan réel |
| `AZURE_REGISTRY`, `AZURE_REGISTRY_USERNAME`, `AZURE_REGISTRY_PASSWORD` | le miroir ACR — absents, donc le miroir saute |

Et une **variable**, pas un secret :

| Variable | Utilisé par |
|---|---|
| `TF_VAR_SSH_PUBLIC_KEY` | la clé publique autorisant l'accès à la machine |

GitHub ne relit jamais un secret : « présent » et « correct » sont deux
affirmations différentes, et seul un `terraform plan` réel distingue les deux.

## Swagger

Exposé par microservice, il n'y a pas de Swagger unique agrégé par la gateway.

`http://localhost:8080/api/v1/swagger-ui/index.html` pour `auth-service`, et
idem en remplaçant le port.

## Conventions de branches

```
feat/    nouvelle fonctionnalité
fix/     correction de bug
chore/   outillage, Dockerfiles, poms
ci/      GitHub Actions, linters, couverture
test/    ajout ou réparation de tests
infra/   Terraform, Kubernetes, Helm
docs/    documentation
hotfix/  correction critique en production
```

Trunk-based : `main` toujours déployable, branches courtes, intégration continue
par PR.

## État vérifié, et ce qui ne l'est pas

**Vérifié par exécution :** 102 tests verts, la CI à 6/6, la release à 26/26 avec
images publiées, `terraform validate` et `fmt`, un `terraform plan` réel contre
l'abonnement donnant 9 ressources, `kubectl kustomize` rendant 22 documents,
`helm lint` et `helm template` rendant 21 documents sans erreur structurelle, 17
tests de chevauchement CIDR.

**Non vérifié :** aucun `terraform apply`, donc rien n'existe sur Azure. Aucun
cluster n'a été créé, donc aucun pod n'a été planifié. Le miroir ACR n'a jamais
poussé, faute de secrets. Et la vérification fonctionnelle en navigateur n'a pas
été faite : le build vert avec `strictTemplates` garantit la compilation des
templates, pas le rendu.
