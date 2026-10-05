# Appliquer l'infrastructure Azure

**Rien n'a encore été créé sur Azure.** Aucune VM ne tourne, aucun registre ACR
n'existe.

L'infrastructure se déploie par **la pipeline**, déclenchée manuellement depuis
l'onglet Actions. Ce document explique la marche à suivre.

## Ce que ça va créer, et ce que ça coûte

| Ressource | Coût |
|---|---|
| compte de stockage de l'état | quelques centimes par mois |
| `azurerm_resource_group.crm` | gratuit |
| `azurerm_virtual_network`, `subnet`, `network_security_group` | gratuit |
| `azurerm_public_ip` × 2 | gratuit tant qu'aucune VM n'y est attachée |
| `azurerm_container_registry.crm` | stockage et opérations, négligeable |
| `azurerm_network_interface` | gratuit |
| **`azurerm_linux_virtual_machine.vm`** | **~30–40 €/mois, facturé à la minute** |

Le disque est facturé même machine arrêtée. Un labo laissé tourner un mois aura
consommé le crédit gratuit.

```bash
az vm stop -g crm-infra -n crm-infra-vm     # arrêter
az vm start -g crm-infra -n crm-infra-vm    # redémarrer
```

## Préalable : la clé SSH

`ssh_public_key` n'a **aucun défaut**, volontairement : une machine avec une clé
autorisée est une machine où quelqu'un peut se connecter.

```bash
ssh-keygen -t ed25519 -C "crm-dev"
```

La clé privée ne quitte jamais ta machine. Seul le contenu de
`~/.ssh/id_ed25519.pub` est mis dans la variable du dépôt :

```bash
gh variable set TF_VAR_ssh_public_key --body "$(cat ~/.ssh/id_ed25519.pub)"
```

C'est une **variable**, pas un secret : une clé publique n'autorise rien seule, et
le fait de pouvoir la lire rend une valeur erronée visible immédiatement.

La pipeline refuse de planifier si cette variable est absente, et refuse aussi si
elle contient encore la clé jetable du chemin « pull request ». Sans cette
protection, un `apply` créerait une machine où personne ne peut se connecter.

## Préalable : la variable du dépôt

Crée le fichier `.tfvars` localement pour les valeurs qui changent, ou passe-les
par variables. Les deuxplus utiles :

```bash
# TON adresse IP, pour restreindre SSH
curl -s https://ifconfig.me
```

| Valeur | Où |
|---|---|
| `TF_VAR_ssh_public_key` | variable du dépôt, ci-dessus |
| `allowed_ssh_source` | `terraform.tfvars` local, `X.X.X.X/32` |

**C'est le seul réglage qui vaille la peine de changer avant la première fois.** Le
défaut est `0.0.0.0/0`, c'est-à-dire accessible depuis tout l'Internet.

## Le parcours

Depuis l'onglet **Actions**, lance **terraform** → *Run workflow*.

Trois entrées :

| Entrée | Valeur |
|---|---|
| `confirm` | `APPLY` — sinon le job `plan` s'arrête |
| `destroy` | `false` — `true` planifie une destruction complète |

Ce qui se passe ensuite :

| Job | Rôle |
|---|---|
| `check` | `fmt` et `validate` avec les mêmes credentials que sur PR |
| `bootstrap` | crée le compte de stockage qui portera l'état, s'il n'existe pas |
| `plan` | planifie **contre l'état réel** et enregistre le plan |
| `apply` | **attend une approbation**, puis applique le plan enregistré |

Le job `apply` utilise `terraform apply tfplan`, pas `terraform apply`. C'est la
différence entre appliquer le plan qui a été approuvé et replanifier au moment de
l'application, ce qui créerait ce qu'Azure regarde entre les deux.

Compte **5 à 10 minutes** pour l'application. La VM est lente à démarrer et le
cloud-init installe Docker, Java 17, Node 20 et kubeadm.

### Ce qu'il faut lire avant d'approuver

- `9 to add`, et **`0 to destroy`** — une destruction n'est pas normale ici
- `size = "Standard_B2s"` — si tu as changé cette valeur, le coût change
- `ssh_public_key` est bien **ta** clé
- `allowed_ssh_source` est ton adresse, pas `0.0.0.0/0`
- le nom du groupe de ressources est `crm-infra`

## Récupérer les sorties

Le résumé du run `apply` affiche `terraform output`. Les trois valeurs utiles :

| Sortie | Où la mettre |
|---|---|
| `acr_login_server` | secret `AZURE_REGISTRY` |
| `vm_public_ip` | pour `ssh` |
| `vm_ssh_command` | la commande de connexion, déjà formatée |

## Brancher le miroir ACR

Dans cet ordre, parce que chaque étape dépend de la précédente.

**a. Créer le principal de service avec le rôle `AcrPush`.**

Le compte admin du registre est désactivé (`acr_admin_enabled = false`), parce
qu'il donne le contrôle total et ne peut pas être restreint.

```bash
ACR_LOGIN=$(az acr show -n crmregistry -g crm-infra --query loginServer -o tsv)
APP_ID=$(az ad app create --display-name crm-acr-push \
  --service-account-name crm-acr-push-$(date +%s) -o json | jq -r .appId)
PASS=$(az ad sp create --app-id "$APP_ID" -o json | jq -r .password)

az role assignment create \
  --assignee "$APP_ID" \
  --role AcrPush \
  --scope "$(az acr show -n crmregistry --query id -o tsv)"
```

`AcrPush` suffit : pousser, sans lire la configuration ni supprimer le registre.

**b. Poser les trois secrets.**

```bash
echo "$ACR_LOGIN" | gh secret set AZURE_REGISTRY
echo "$APP_ID"    | gh secret set AZURE_REGISTRY_USERNAME
echo "$PASS"      | gh secret set AZURE_REGISTRY_PASSWORD
```

Le `USERNAME` est l'**app id du principal**, pas un nom de compte du registre.

**c. Vérifier le miroir.**

```bash
git tag v0.1.0
git push origin v0.1.0
```

Le job `mirror to Azure Container Registry` doit montrer `Publish to ACR`
**exécuté** et non `skipped`. La release `37261925170` l'a sauté, parce que les
trois secrets étaient absents.

## Le cluster

Une fois la VM allumée et cloud-init terminé :

```bash
ssh azureuser@<vm_public_ip>

export CRM_PUBLIC_IP=<ton IP publique>
cd infra/kubernetes-kubeadm-cluster
sudo ./init-control-plane.sh
```

`CRM_PUBLIC_IP` sert à mettre l'adresse publique dans les SAN du certificat de
l'API server. Sans elle, le kubeconfig recopié sur une autre machine échoue en
`x509`, ce qui ressemble à un problème réseau.

## En local, pour déboguer

Le backend est `azurerm` avec des valeurs vides, fournies à l'init :

```bash
cd infra/terraform-azure-vms-and-acr
ACCOUNT="crmtf$(gh api repos/ylhan237/CRM-devops-platform --jq .id)"
terraform init \
  -backend-config="resource_group=crm-state" \
  -backend-config="storage_account_name=${ACCOUNT}" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=access_key" \
  -backend-config="use_azuread_auth=true"

terraform plan
terraform state list
```

`tfvars` reste utile en local pour itérer sans déclencher la pipeline.
Le nom du compte de stockage combine `crmtf` avec l'ID immuable du dépôt GitHub :
Azure exige que ce nom soit unique à l'échelle mondiale. La pipeline crée ce même
compte dans `crm-state`.

## Si quelque chose se passe mal

**Le plan échoue sur les credentials.** Un abonnement, un tenant ou un rôle
Contributor manquant. Le rôle manquant est le cas le plus fréquent.

**La VM démarre mais SSH refuse la connexion.** Pres toujours
`allowed_ssh_source` qui ne contient pas ton adresse actuelle, ton adresse a
changé, ou la clé est mauvaise. Depuis le portail Azure, « Réinitialiser les
identifiants » régénère la configuration sans toucher au disque.

**L'application est interrompue.** L'état est distant et le verrou est libéré, donc
relance le workflow : il repart où ça s'est arrêté.

**Une ressource bloque.** En local :

```bash
terraform destroy -target=azurerm_linux_virtual_machine.vm
```

`-target` est à utiliser avec parcimonie : il produit un état qui ne correspond pas
à la configuration, et le prochain plan en tient compte.

**Tout effacer.** Cocher `destroy: true` et `confirm: APPLY` dans le workflow.
Comme `prevent_deletion_if_contains_resources` est actif, le groupe de ressources
refuse d'être supprimé s'il reste quelque chose dedans.
