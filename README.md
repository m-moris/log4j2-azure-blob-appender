# Azure Blob Appender

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.m-moris/log4j2-azure-blob-appender)](https://search.maven.org/search?q=a:log4j2-azure-blob-appender)

This is a custom appender for log4j2 that outputs logs to Azure Storage Blob. There are two methods, one is to specify storage account and key directly, and the other is to use application diagnostic log of Azure App Service (WebApps).

## What's New

**2025/02**

Update dependent libraries

**Important 2024/03**

Managed ID is now supported. It uses `DefaultAzureCredential` internally.

**Updated 2024/3/11**

- Update dependent libraries
- JDK version has been changed from 8 to 11

**Important 2022/4/28**

The version of Azure SDK Blob used internally has been updated from V8 to V12. However, there is no change in the interface.
Along with that, the tool version has been changed to V2. 

## WebApps mode

App Service has an app diagnostic logging feature, which is valid only for .NET applications and not available from Java, but it can be solved. However, it is only when the execution environment is Windows.

[Enable diagnostics logging for apps - Azure App Service | Microsoft Docs](https://docs.microsoft.com/en-us/azure/app-service/troubleshoot-diagnostic-logs)

### How it works

Enabling App Diagnostic Logging for App Service sets the Storage URL with SAS in the `DIAGNOSTICS_AZUREBLOBCONTAINERSASURL` environment variable. Azure Blob Appender will output a log to append to this blob.

The environment variables to refer to are as follows, in other words, it can be used if the following environment variables are set.

* `DIAGNOSTICS_AZUREBLOBCONTAINERSASURL`
* `WEBSITE_SITE_NAME`
* `WEBSITE_INSTANCE_ID`

Linux-based WebApps are not available because app diagnostic logging feature is not provided.

## General mode

Directly specify a storage account or key in the configuration file.

### Items to be set

The items to be set are as follows. It is necessary to specify Prefix1 / Prefix2 to output in the directory structure described later.

If you use a storage key for authentication, you need the following three values.
* Storage account
* Storage account key
* Container name

If you use managed ID for authentication, you need the following one value.
* BLOB URI including container name (e.g. `https://{your-storage-account-name}.blob.core.windows.net/logs`)

* Prefix 1
* Prefix 2

## BLOB directory structure

In WebApps mode, it outputs with the same structure as the diagnostic log of .NET application.

`{WEBSITE_SITE_NAME}/yyyy/mm/dd/HH/{WEBSITE_INSTANCE_ID}_applicationLog.txt`

The following is the general mode.

`{Prefix1}/yyyy/mm/dd/HH/{Prefix2}_applicationLog.txt`


## Configuraion

| Attribute     | Type    | Mean                                                                              |
| ------------- | ------- | --------------------------------------------------------------------------------- |
| webapps       | boolean | If `true`, the following settings are ignored in WebApps mode. Default is `false` |
| accountName   | string  | Storage account name. if webapps is `false`, it is effective.                     |
| accountKey    | string  | Storage account key. if webapps is `false`, it is effective.                      |
| containerName | string  | The name of blob container. if webapps is `false`, it is effective.               |
| containerUri  | string  | The URI of blob container. if webapps is `false`, it is effective.                |
| prefix1       | string  | Prefix1                                                                           |
| prefix2       | string  | Prefix2                                                                           |

`containerUri` takes precedence.

### Sample configurations

In webapps mode.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="info">
    <Appenders>
        <AzureBlobAppender name="azureblob" webapps="true">
            <PatternLayout pattern="[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %c{1} - %msg%n" />
        </AzureBlobAppender>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="azureblob" />
        </Root>
    </Loggers>
</Configuration>
```


In general mode.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="info">
    <Appenders>
        <AzureBlobAppender name="azureblob" 
                           accountName="<<yourstorageaccount>>>"
                           accountKey="<<yourstorageaccountkey>>"
                           containerName="<<yourblobstoragecontainername>>"
                           containerUri="<<yourblobstoragecontaineruri>>"
                           prefix1="${env:COMPUTERNAME}"
                           prefix2="someprefix">
            <PatternLayout pattern="[%-5level] %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %c{1} - %msg%n" />
        </AzureBlobAppender>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="azureblob" />
        </Root>
    </Loggers>
</Configuration>
```

## Test

Set your account and key to `test/resources/azureconfig.json` 

[To Japanese](./README.ja.md)
