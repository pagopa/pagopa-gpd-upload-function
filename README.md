# pagoPA Functions template

Java template to create an Azure Function.

## Function examples
There is an example of a Http Trigger function.

---

## Run locally with Docker
`docker build -t pagopa-functions-template .`

`docker run -p 8999:80 pagopa-functions-template`

### Test
`curl http://localhost:8999/example`

## Run locally with Maven and Azurite

Use the [Azurite](https://learn.microsoft.com/en-us/azure/storage/common/storage-use-azurite?tabs=visual-studio%2Cblob-storage) emulator for local Azure Storage development

```
docker run -p 10000:10000 -p 10001:10001 -p 10002:10002 \
mcr.microsoft.com/azure-storage/azurite
```
#### Maven
```
mvn clean package
mvn azure-functions:run
```

```
mvn -f pom.xml clean package -Dmaven.test.skip=true && mvn -e azure-functions:run
```
### Test
`curl http://localhost:7071/example` 

---


## TODO
Once cloned the repo, you should:
- to deploy on standard Azure service:
  - rename `deploy-pipelines-standard.yml` to `deploy-pipelines.yml`
  - remove `helm` folder
- to deploy on Kubernetes:
  - rename `deploy-pipelines-aks.yml` to `deploy-pipelines.yml`
  - customize `helm` configuration
- configure the following GitHub action in `.github` folder: 
  - `deploy.yml`
  - `sonar_analysis.yml`

Configure the SonarCloud project :point_right: [guide](https://pagopa.atlassian.net/wiki/spaces/DEVOPS/pages/147193860/SonarCloud+experimental).