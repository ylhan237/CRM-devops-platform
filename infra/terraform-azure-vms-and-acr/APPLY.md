# Appliquer l'infrastructure Azure

Ce document explique comment passer du plan (qui a réussi et a produit 9 ressources)
à la création réelle. **Rien n'a encore été créé sur Azure.** Aucune VM ne tourne,
aucun registre ACR n'existe.

## Ce que ça va créer, et ce que ça coûte

| Ressource | Coût |
|---|---|
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

## Étape 1 — Générer la clé SSH

La variable `ssh_public_key` n'a **aucun défaut**, volontairement : une machine
avec une clé autorisée est une machine où quelqu'un peut se connecter.

```bash
ssh-keygen -t ed25519 -C "crm-dev"
```

La clé privée ne quitte jamais cette machine. Seul le contenu de `~/.ssh/id_ed25519.pub`
sera collé à l'étape 2.

Si tu n'as pas de clé, la machine est inaccessible après le premier démarrage.

## Étape 2 — Remplir `terraform.tfvars`

```bash
cd infra/terraform-azure-vms-and-acr
cp terraform.tfvars.example terraform.tfvars
```

Puis colle le contenu de `~/.ssh/id_ed25519.pub` dans `ssh_public_key`.

**Resserre SSH avant d'appliquer.** C'est le seul réglage qui vaille la peine de
changer avant la première fois. Le défaut est `0.0.0.0/0`, c'est-à-dire accessible
depuis tout l'Internet :

```bash
curl -s https://ifconfig.me
```

Mets le résultat dans `allowed_ssh_source` sous la forme `X.X.X.X/32`.

`terraform.tfvars` est gitignoré, donc rien ne part au dépôt.

## Étape 3 — Relire le plan

```bash
terraform init
terraform plan
```

Le plan a déjà été exécuté contre l'abonnement réel depuis la CI et a donné
`9 to add, 0 to change, 0 to destroy`. Depuis ta machine il devrait être identique.
**Lis-le quand même** : c'est la dernière occasion de voir ce qui va être créé.

Ce que tu dois vérifier :

- `9 to add`, et **`0 to destroy`** — une destruction n'est pas normale ici
- le nom du groupe de ressources est bien `crm-infra`
- `location = "westeurope"`
- `size = "Standard_B2s"` — si tu as changé cette valeur entre-temps, le coût change
- `ssh_public_key` contient **ta** clé, pas celle jetable du workflow
- `allowed_ssh_source` est ton adresse, pas `0.0.0.0/0`

## Étape 4 — Appliquer

```bash
terraform apply
```

Pas de `-auto-approve` la première fois. Terraform affiche le plan, tu confirmes,
ça part.

Compte **5 à 10 minutes**. La VM est lente à démarrer et le cloud-init installe
Docker, Java 17, Node 20 et kubeadm.

## Étape 5 — Récupérer les sorties

```bash
terraform output
```

Trois valeurs comptent :

| Sortie | Où la mettre |
|---|---|
| `acr_login_server` | secret `AZURE_REGISTRY` |
| `vm_public_ip` | pour `ssh` |
| `vm_ssh_command` | la commande de connexion, déjà formatée |

## Étape 6 — Brancher le miroir ACR

Dans cet ordre, parce que chaque étape dépend de la précédente.

**a. Créer le principal de service avec le rôle `AcrPush`.**

Le compte admin du registre est désactivé (`acr_admin_enabled = false`), parce
qu'il donne le contrôle total et ne peut pas être restreint. Il faut donc un
principal qui peut l'être.

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

`AcrPush` suffit : il permet de pousser, pas de lire la configuration ni de
supprimer le registre.

**b. Poser les trois secrets GitHub.**

```bash
echo "$ACR_LOGIN" | gh secret set AZURE_REGISTRY
echo "$APP_ID"    | gh secret set AZURE_REGISTRY_USERNAME
echo "$PASS"      | gh secret set AZURE_REGISTRY_PASSWORD
```

Le `USERNAME` est l'**app id du principal**, pas le nom de compte du registre, et
pas l'admin.

## Étape 7 — Vérifier le miroir

Relance la Release :

```bash
git tag v0.1.0
git push origin v0.1.0
```

Cette fois le job `mirror to Azure Container Registry` doit montrer `Publish to ACR`
**exécuté** et non `skipped`. La release précédente
(`37261925170`) l'a sauté, parce que les trois secrets étaient absents.

Rien à reconfigurer : le miroir s'active de lui-même dès que les secrets existent.

## Étape 8 — Le cluster

Une fois la VM allumée et cloud-init terminé :

```bash
ssh azureuser@$(terraform output -raw vm_public_ip)

export CRM_PUBLIC_IP=<ton IP publique>
cd infra/kubernetes-kubeadm-cluster
sudo ./init-control-plane.sh
```

`CRM_PUBLIC_IP` sert à mettre l'adresse publique dans les SAN du certificat de
l'API server. Sans elle, le kubeconfig recopié sur une autre machine échoue en
`x509`, ce qui ressemble à un problème réseau.

## Si quelque chose se passe mal

**Le plan échoue sur les credentials.** Un abonnement, un tenant ou un rôle
Contributor manquant. Le message Azure nomme la cause ; le rôle manquant est le
cas le plus fréquent.

**La VM démarre mais SSH refuse la connexion.** Pres toujours `allowed_ssh_source`
qui ne contient pas ton adresse actuelle, ton adresse a changé, ou la clé est
mauvaise. Depuis le portail Azure, « Réinitialiser les identifiants » régénère la
configuration sans toucher au disque.

**`terraform apply` est interrompu.** L'état est conservé et un nouvel `apply`
reprend où ça s'est arrêté. Un apply interrompu ne laisse pas les ressources à
moitié créées : certaines le sont, d'autres non, et `plan` dira quoi reste.

**Une ressource bloque un nouvel apply.**

```bash
terraform state list
terraform destroy -target=azurerm_linux_virtual_machine.vm
terraform apply
```

`-target` est à utiliser avec parcimonie : il produit un état qui ne correspond pas
à la configuration, et le prochain plan en tient compte.

**Tout effacer et repartir.**

```bash
terraform destroy
```

Comme `prevent_deletion_if_contains_resources` est actif, le groupe de ressources
refuse d'être supprimé s'il reste quelque chose dedans. Vide-le d'abord.
