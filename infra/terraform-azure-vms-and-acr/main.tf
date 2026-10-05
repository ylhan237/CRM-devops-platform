# Azure landing zone for the CRM backend: a container registry the release
# workflow mirrors into, and a machine to run the services and, later, the
# cluster on.
#
# The registry comes first on purpose. The release workflow already publishes to
# GHCR and already knows how to mirror, but the mirror needs a registry to
# mirror into and three secrets that point at it. Provisioning it here is what
# makes that half of the pipeline real rather than a notice.

locals {
  name = "${var.name_prefix}-infra"

  common_tags = merge(var.tags, {
    component = "landing-zone"
  })

  # Ports the services listen on, named so the security group rules read as
  # something rather than as a list of numbers.
  #
  # The priority is 200 + position, not 200 + port. Azure only accepts priorities
  # between 100 and 4096, and deriving it from the port number gives 6643 for the
  # kubeadm port and 8961 for the service registry, which the provider rejects. Both
  # of those were rejected on the first plan, so the numbers a reader would expect
  # to see in a security group are not the numbers Azure will take.
  application_rules = { for position, port in var.application_ports : port => {
    port     = port
    priority = 200 + position
  } }

  # Installed by cloud-init on first boot.
  #
  # Docker and its compose plugin, a JDK, Node, and the container CLI tools the
  # Kubernetes stage will need. Nothing is built on the machine: images come from
  # the registry, which is the point of the registry.
  cloud_init = <<-EOT
    #!/bin/bash
    set -euo pipefail
    exec > >(tee /var/log/cloud-init-output.log | logger -t cloud-init -s 2>/dev/console) 2>&1

    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y ca-certificates curl gnupg git jq

    # Docker, from the distribution repository rather than the convenience
    # script: the script pipes a download into a shell as root.
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
      > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Java 17 is the version every module compiles against, which is why the
    # build parent pins it.
    apt-get install -y openjdk-17-jdk-headless

    # Node 20, the major the Angular 18 frontend expects.
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs

    # The Kubernetes prerequisites. kubeadm itself is configured by the cluster
    # stage, not here.
    curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor -o /etc/apt/keyrings/kubernetes-archive-keyring.gpg
    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-archive-keyring.gpg] https://apt.kubernetes.io/ kubernetes-xenial main" \
      > /etc/apt/sources.list.d/kubernetes.list
    apt-get update
    apt-get install -y kubelet kubeadm kubectl
    apt-mark hold kubelet kubeadm kubectl

    systemctl enable --now docker

    # The administrative account is created with no password and the key only,
    # so there is no password to leak or to guess.
    echo "${var.admin_username} ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/90-crm
    chmod 0440 /etc/sudoers.d/90-crm
  EOT
}

resource "azurerm_resource_group" "crm" {
  name     = local.name
  location = var.location
  tags     = local.common_tags
}

# ---------------------------------------------------------------- registry ----

resource "azurerm_container_registry" "crm" {
  name                = "${var.name_prefix}registry"
  resource_group_name = azurerm_resource_group.crm.name
  location            = azurerm_resource_group.crm.location
  sku                 = var.acr_sku
  admin_enabled       = var.acr_admin_enabled
  tags                = local.common_tags

  # No zone redundancy and no public network access block: this is a single
  # registry for a lab, and denying public access here would stop the release
  # workflow pushing until the network rules are worked out.
}

# ---------------------------------------------------------------- network ----

resource "azurerm_virtual_network" "crm" {
  name                = "${local.name}-vnet"
  address_space       = ["10.20.0.0/16"]
  location            = azurerm_resource_group.crm.location
  resource_group_name = azurerm_resource_group.crm.name
  tags                = local.common_tags
}

resource "azurerm_subnet" "internal" {
  name                 = "internal"
  resource_group_name  = azurerm_resource_group.crm.name
  virtual_network_name = azurerm_virtual_network.crm.name
  address_prefixes     = ["10.20.1.0/24"]
}

resource "azurerm_network_security_group" "crm" {
  name                = "${local.name}-nsg"
  location            = azurerm_resource_group.crm.location
  resource_group_name = azurerm_resource_group.crm.name
  tags                = local.common_tags

  security_rule {
    name                       = "ssh"
    description                = "SSH, restricted to allowed_ssh_source"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.allowed_ssh_source
    destination_address_prefix = "*"
  }

  dynamic "security_rule" {
    for_each = local.application_rules

    content {
      name                       = "app-${security_rule.value.port}"
      description                = "Application port ${security_rule.value.port}"
      priority                   = security_rule.value.priority
      direction                  = "Inbound"
      access                     = "Allow"
      protocol                   = "Tcp"
      source_port_range          = "*"
      destination_port_range     = tostring(security_rule.value.port)
      source_address_prefix      = "*"
      destination_address_prefix = "*"
    }
  }
}

resource "azurerm_public_ip" "vm" {
  name                = "${local.name}-ip"
  location            = azurerm_resource_group.crm.location
  resource_group_name = azurerm_resource_group.crm.name
  allocation_method   = "Static"
  # Standard is the only SKU that stays reachable from the internet; Basic is
  # deprecated and its addresses are not routable inbound.
  sku  = "Standard"
  tags = local.common_tags
}

resource "azurerm_network_interface" "vm" {
  name                = "${local.name}-nic"
  location            = azurerm_resource_group.crm.location
  resource_group_name = azurerm_resource_group.crm.name
  tags                = local.common_tags

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.internal.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.vm.id
  }
}

# --------------------------------------------------------------- machine ----

resource "azurerm_linux_virtual_machine" "vm" {
  name                = "${local.name}-vm"
  computer_name       = "${var.name_prefix}-vm"
  location            = azurerm_resource_group.crm.location
  resource_group_name = azurerm_resource_group.crm.name
  size                = var.vm_size
  admin_username      = var.admin_username
  tags                = local.common_tags

  network_interface_ids = [azurerm_network_interface.vm.id]

  admin_ssh_key {
    username   = var.admin_username
    public_key = trimspace(var.ssh_public_key)
  }

  # Key only. In azurerm 4.x the admin_ssh_key block is what enables key
  # authentication, and disable_password_authentication keeps the password path
  # closed. There is no admin_ssh_key_enabled argument any more; it existed in
  # 2.x and this configuration failed to validate against 4.81 until it went.
  disable_password_authentication = true

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Premium_LRS"
    disk_size_gb         = var.vm_disk_size_gb
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  # cloud-init, base64 encoded as the provider expects.
  custom_data = base64encode(local.cloud_init)
}

# The egress address of the machine, which is what the container registry
# firewall rule and the Kubernetes allowlist would use.
resource "azurerm_public_ip" "gateway" {
  name                = "${local.name}-gateway"
  location            = azurerm_resource_group.crm.location
  resource_group_name = azurerm_resource_group.crm.name
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = local.common_tags
}