# GPD Upload Functions

A set of Azure functions related to debt positions massive upload.

## Functions

| Function            | Trigger      | Description                               |
|---------------------|--------------|-------------------------------------------|
| ValidationFunction  | QueueTrigger | Validate Blob on CreatedEvent             |
| ServiceFunction     | QueueTrigger | Perform CRUD operations on debt-positions |

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
