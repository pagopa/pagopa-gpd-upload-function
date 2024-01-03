prefix    = "pagopa"
env       = "dev"
env_short = "d"
location  = "westeurope"

tags = {
  CreatedBy   = "Terraform"
  Environment = "Dev"
  Owner       = "pagoPA"
  Source      = "https://github.com/pagopa/pagopa-gpd-upload-function"
  CostCenter  = "TS310 - PAGAMENTI & SERVIZI"
}

cd_github_federations = [
  {
    repository = "pagopa-gpd-upload-function"
    subject    = "dev"
  }
]

environment_cd_roles = {
  subscription = [
    "Contributor",
    "Storage Account Contributor",
    "Storage Blob Data Contributor",
    "Storage File Data SMB Share Contributor",
    "Storage Queue Data Contributor",
    "Storage Table Data Contributor",
    "Key Vault Contributor"
  ]
  resource_groups = {
    "pagopa-d-gps-sec-rg" = [
      "Key Vault Contributor"
    ],
    "pagopa-d-weu-dev-aks-rg" = [
      "Contributor"
    ]
  }
}
