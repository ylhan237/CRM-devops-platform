terraform {
  required_version = ">= 1.6.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # The state holds the ACR login server and the generated resource names.
  # It is kept out of git on purpose; see .gitignore.
  backend "local" {}
}