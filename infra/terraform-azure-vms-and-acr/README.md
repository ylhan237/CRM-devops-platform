# Azure landing zone

Provisions what the rest of the chain needs on Azure:

- an **Azure Container Registry**, which is what the release workflow mirrors into
- a **virtual machine** carrying Docker, Java 17 and Node 20, on which the
  services and then the Kubernetes cluster will run

Branch `infra/terraform-azure-vms-and-acr`, stage 4.1 of `note.md` section 4.3.

## Before anything else: cost

This is the first configuration in the project that spends money. The registry
bills for storage and operations, which is negligible. **The virtual machine
bills for every minute it runs**, roughly 30 to 40 EUR a month for
`Standard_B2s`, and its disk bills even while the machine is stopped.

Stop it when you are not using it:

```bash
az vm stop -g crm-infra -n crm-vm
```

Start it again with `az vm start`. A lab that has been left running for a month
will have consumed the free credit.

## Credentials

Terraform reads the four values from the environment, so no secret is ever
written to a file:

```
ARM_SUBSCRIPTION_ID
ARM_TENANT_ID
ARM_CLIENT_ID
ARM_CLIENT_SECRET
```

They come from an **app registration** in Microsoft Entra, given the
**Contributor** role on the subscription. A service principal with no role is the
most common reason a first `terraform apply` fails.

They can be set as GitHub Actions secrets so the pipeline can run this, or
exported in a shell for a local run. `README` for the rest of the project
describes where each is found in the portal.

## Usage

```bash
# 1. Generate a key, once. Only the public half ever goes into Terraform.
ssh-keygen -t ed25519 -C "crm-dev"

# 2. Describe the environment.
cp terraform.tfvars.example terraform.tfvars
# then paste the contents of ~/.ssh/id_ed25519.pub into ssh_public_key

# 3. Narrow SSH to your own address. This is the one setting worth changing
#    before the first apply.
#    curl -s https://ifconfig.me

terraform init
terraform plan
terraform apply
```

`terraform.tfvars` is gitignored. `terraform.tfvars.example` is not, and holds no
secret, only the shape of the answer.

## Wiring the release workflow to the registry

After `apply`, three of the outputs are what the release workflow wants:

| Output | Where it goes |
|---|---|
| `acr_login_server` | the `AZURE_REGISTRY` secret |
| — | `AZURE_REGISTRY_USERNAME`, the principal's client id |
| — | `AZURE_REGISTRY_PASSWORD`, the client secret value |

The username is the **client id of the service principal** given the **AcrPush**
role on the registry. It is not the admin account, because `acr_admin_enabled` is
false by default.

Until those three secrets exist, the mirror job in `release.yml` announces that
it is skipping and the release succeeds against GHCR alone. Nothing has to be
re-run after adding them: the next release picks them up.

## What was verified, and what was not

`terraform init`, `terraform fmt` and `terraform validate` all pass against
azurerm 4.81.0. `validate` checks the configuration and the provider schema, not
the values: it will not tell you the subscription is wrong or the quota is
exceeded.

**No `apply` has been run.** The first one is where Azure surprises appear, and
`terraform plan` should be read rather than skimmed.