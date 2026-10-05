variable "location" {
  description = <<-EOT
    Region everything is created in.

    germanywestcentral, and not westeurope: the subscription carries an Azure policy
    named sys.regionrestriction that restricts deployments to a fixed list, and
    westeurope is not on it. Creating anything there fails with

      RequestDisallowedByAzure ... This policy maintains a set of best available
      regions where your subscription can deploy resources

    which reads like a quota problem and is not. The regions this subscription may
    use are

      germanywestcentral   spaincentral   italynorth   swedencentral   polandcentral

    germanywestcentral is the default because it is the closest to France of those
    five. Change it freely if the policy list changes; it is a variable precisely
    because that list is not something this repository controls.
  EOT
  type        = string
  default     = "germanywestcentral"
}

variable "name_prefix" {
  description = "Prefix for every resource name. Change it to keep two environments apart."
  type        = string
  default     = "crm"
}

variable "vm_size" {
  description = <<-EOT
    Size of the virtual machine.

    Standard_B1s is the cheapest but has 1 GB of RAM, which is not enough to
    build a Java image or to run a kubeadm control plane. Standard_B2s is the
    smallest size that can do both.

    This is the one resource here that costs money every minute it runs. Stop the
    machine when you are not using it: a stopped machine bills for its disk only.
  EOT
  type        = string
  default     = "Standard_B2s"
}

variable "admin_username" {
  description = "Local administrator account on the machine."
  type        = string
  default     = "azureuser"
}

variable "ssh_public_key" {
  description = <<-EOT
    Public key authorised for the administrator account.

    There is no default on purpose. Generate one with:

      ssh-keygen -t ed25519 -C "crm-dev"

    and paste the contents of the .pub file here, in terraform.tfvars, which is
    gitignored. Only the public half goes here; the private half never leaves the
    machine that generated it.
  EOT
  type        = string

  validation {
    condition     = can(regex("^(ssh-|ecdsa-)", trimspace(var.ssh_public_key)))
    error_message = "ssh_public_key must start with ssh- or ecdsa-. Run ssh-keygen -t ed25519 and paste the contents of the .pub file."
  }
}

variable "acr_sku" {
  description = <<-EOT
    SKU of the container registry.

    Standard, not Basic, because a service principal can only be granted AcrPush
    on Standard and above. The release workflow pushes to Azure with a principal
    rather than with the registry admin account, so Basic would not work.
  EOT
  type        = string
  default     = "Standard"
}

variable "acr_admin_enabled" {
  description = <<-EOT
    Whether the registry admin account is enabled.

    false by default. The release workflow authenticates with a service
    principal, which is the credential that can be scoped to AcrPush. Set this
    to true only if you have no principal available, and understand that the
    admin password grants full control of the registry.
  EOT
  type        = bool
  default     = false
}

variable "allowed_ssh_source" {
  description = <<-EOT
    Address range allowed to reach SSH.

    "0.0.0.0/0" is open to the whole internet and is only the right answer on a
    lab that lives for a day. Narrow it to your own address as soon as you know
    it, and expect to change it when your address changes:

      curl -s https://ifconfig.me
  EOT
  type        = string
  default     = "0.0.0.0/0"
}

variable "application_ports" {
  description = <<-EOT
    Ports the application listens on, opened in addition to SSH.

    8060 api-gateway, 8080 auth-service, 8761 eureka, 8082 student,
    8084 task, 8085 notification, 8089 event, 4200 frontend in development.

    6443 is the kubeadm control plane API, added now so the machine does not need
    a new rule when the cluster stage provisions.
  EOT
  type        = list(number)
  default     = [6443, 8060, 8080, 8082, 8084, 8085, 8089, 8761]

  validation {
    # A repeated port produces two rules with different priorities and the same
    # destination, and Azure lets them coexist, so nothing complains and one of them
    # is unreachable. Refusing it here is cheaper than finding that out later.
    condition     = length(distinct(var.application_ports)) == length(var.application_ports)
    error_message = "application_ports contains a duplicate. Each port may appear once."
  }

  validation {
    # 22 is excluded because SSH already has a rule at priority 100, and two rules for
    # one port means one of them is ignored without any warning.
    condition = alltrue([
      for port in var.application_ports :
      port >= 1 && port <= 65535 && port != 22
    ])
    error_message = "Each port must be between 1 and 65535, and must not be 22, which the SSH rule already covers."
  }

  validation {
    # Azure accepts priorities from 100 to 4096 and the rules start at 200, so a list
    # longer than 3896 puts the last rule out of range. The first version derived the
    # priority from the port number and the kubeadm port alone produced 6643.
    condition     = length(var.application_ports) <= 3896
    error_message = "Too many ports: priorities start at 200 and Azure rejects anything above 4096, so the list may hold at most 3896 entries."
  }
}

variable "vm_disk_size_gb" {
  description = "Size of the OS disk. Docker images and a Kubernetes cluster need room."
  type        = number
  default     = 60
}

variable "tags" {
  description = "Tags applied to every taggable resource."
  type        = map(string)
  default = {
    project = "crm-devops"
    managed = "terraform"
  }
}