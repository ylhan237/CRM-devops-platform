output "resource_group_name" {
  description = "Name of the resource group holding everything."
  value       = azurerm_resource_group.crm.name
}

output "acr_login_server" {
  description = <<-EOT
    Login server of the container registry.

    This is the value of the AZURE_REGISTRY GitHub secret. It is the host only,
    with no scheme and no trailing slash: acrlogin.azurecr.io, not
    https://acrlogin.azurecr.io.
  EOT
  value       = azurerm_container_registry.crm.login_server
}

output "acr_repository_name" {
  description = "Name of the registry, which is the first segment of an image reference."
  value       = azurerm_container_registry.crm.name
}

output "acr_admin_password" {
  description = <<-EOT
    Password of the registry admin account.

    Empty unless acr_admin_enabled is true. Prefer a service principal: this
    credential grants full control of the registry and cannot be scoped.
  EOT
  value       = var.acr_admin_enabled ? azurerm_container_registry.crm.admin_password : ""
  sensitive   = true
}

output "vm_id" {
  description = "Resource id of the machine, used to stop and start it."
  value       = azurerm_linux_virtual_machine.vm.id
}

output "vm_name" {
  description = "Name of the machine."
  value       = azurerm_linux_virtual_machine.vm.name
}

output "vm_public_ip" {
  description = "Public address of the machine, and the host to put in ssh."
  value       = azurerm_public_ip.vm.ip_address
}

output "vm_ssh_command" {
  description = "The command to run once apply has finished."
  value       = "ssh ${var.admin_username}@${azurerm_public_ip.vm.ip_address}"
}

output "allowed_ssh_source" {
  description = <<-EOT
    Address range currently allowed to reach SSH.

    If this is 0.0.0.0/0 the machine is reachable from anywhere. Narrow it, then
    apply again, and expect to do so again when your own address changes.
  EOT
  value       = var.allowed_ssh_source
}

output "costs" {
  description = "Which resources here cost money."
  value = {
    registry = "Storage and operations only, cheap"
    vm       = "Billed for every minute it runs. Stop it when unused."
    disk     = "Billed even when the machine is stopped."
  }
}