terraform {
  required_version = ">= 1.6.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # The state holds the ACR login server, the machine's resource id and every
  # generated name. It is kept out of git on purpose.
  #
  # It is also kept out of *any single machine*, which is why this is the azurerm
  # backend rather than the local one it started as. With `backend "local"` the state
  # belongs to whichever computer ran apply, so:
  #
  #   - a run from a laptop and a run from CI would each believe they own everything
  #   - a CI runner is discarded, so its state is discarded with it, and the next run
  #     would try to create all nine resources again
  #   - two people applying at once would not know about each other
  #
  # The azurerm backend also takes a lock, so two runs cannot apply at the same time.
  #
  # The values are deliberately empty. They are supplied at init time with
  # -backend-config, so the location of the state is not in the repository and a fork
  # cannot redirect this at somebody else's storage account. The pipeline sets:
  #
  #   resource_group, storage_account_name, container_name, use_azuread_auth
  #
  # Locally, copy them from APPLY.md. An empty backend block with no -backend-config
  # fails at init with a message naming the missing key, which is the intended
  # failure: it cannot be silently defaulted.
  backend "azurerm" {}
}