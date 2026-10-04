# Credentials come from the environment, never from the configuration:
#
#   ARM_SUBSCRIPTION_ID
#   ARM_TENANT_ID
#   ARM_CLIENT_ID
#   ARM_CLIENT_SECRET
#
# The azurerm provider reads all four by default, so there is nothing to fill in
# here and no secret can end up in a file that gets committed.
provider "azurerm" {
  features {
    resource_group {
      # A resource group that still holds something must not be destroyed by a
      # mistyped name. The cost of refusing is one extra command; the cost of not
      # refusing is the whole environment.
      prevent_deletion_if_contains_resources = true
    }
  }
}