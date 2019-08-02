# Azure Blob Appender 

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.m-moris/log4j2-azure-blob-appender)](https://search.maven.org/search?q=a:log4j2-azure-blob-appender)

Azure Storage Blob にログを出力する log4j2用のカスタムアペンダーです。ストレージアカウントやキーをなどを直接指定する方法と、Azure App Service  (WebApps) のアプリケーション診断ログを利用する方法があります。

## WebAppsモード

AApp Service には、アプリ診断ログ機能がありますが、これは .NET アプリケーションでのみ有効で、Javaから利用できませんが、これを解決することができます。ただし実行環境がWindowsの場合のみです。

[アプリの診断ログの有効化 - Azure App Service | Microsoft Docs](https://docs.microsoft.com/ja-jp/azure/app-service/troubleshoot-diagnostic-logs)

### 仕組み

App Service のアプリ診断ログを有効にすると、`DIAGNOSTICS_AZUREBLOBCONTAINERSASURL` 環境変数にSAS付きのStorage URLが設定されます。Azure Blob Appender はこのBLOBに追記するようにログを出力します。

参照する環境変数は以下の通りです、逆に言えば以下の環境変数が設定されていれば利用できます。

* `DIAGNOSTICS_AZUREBLOBCONTAINERSASURL`
* `WEBSITE_SITE_NAME`
* `WEBSITE_INSTANCE_ID`

Linux ベースの WebAppsには、アプリ診断ログ機能が用意されていないので利用できませんが。ただし、該当の環境変数を設定しておけば、動作すると思います。

## 一般モード

構成ファイルにストレージアカウントやキーを直接指定する方法です。

### 設定すべき項目

設定すべき項目は以下の通りです。後述するディレクトリ構造で出力するためPrefix1/Prefix2の指定が必要です。

* ストレージアカウント
* ストレージアカウントキー
* Prefix1 
* Prefix2

## BLOBのディレクトリ

WebAppsモードでは、 .NET アプリの診断ログと同じ構造で出力します。

`{WEBSITE_SITE_NAME}/yyyy/mm/dd/HH/{WEBSITE_INSTANCE_ID}_applicationLog.txt`

一般モードでは以下となります。

`{Prefix1}/yyyy/mm/dd/HH/{Prefix2}_applicationLog.txt`

log4j2の構成ファイルでは環境変数を参照できるので、実行する環境に適切な値を設定できます。

## 構成ファイル

設定する属性は以下の通り。

|属性|型|意味|
|-----|-----|-----|
|webapps| boolean | `true`ならWebAppsモードで、以下の設定は無視される。デフォルトは`false`|
|accountName|string|ストレージアカウント名。webappsが`false`なら有効|
|accountKey|string|ストレージアカウントキー。同上|
|prefix1| string | Prefix1。同上|
|prefix2|string|Prefix2。同上|

### 定義のサンプル

WebAppsモード
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


一般モード

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="info">
    <Appenders>
        <AzureBlobAppender name="azureblob" 
                           accountName="<<yourstorageaccount>>>"
                           accountKey="<<yourstorageaccountkey>>"
                           prefix1="${sys:COMPUTERNAME}"
                           prefix2="foobar">
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

## テスト

`test/resources/azureconfig.json` に値を設定すること。

以上