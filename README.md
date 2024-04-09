<h1 align="center"><img src="https://iam.volccdn.com/obj/volcengine-public/pic/volcengine-icon.png"></h1>
<h1 align="center">火山引擎Android SDK for TLS</h1> 
欢迎使用火山引擎SDK for Android，本文档为您介绍如何获取及调用SDK。

## 前置准备

### 服务开通

请确保您已开通了日志服务。您可前往[火山引擎控制台](https://console.volcengine.com/ )，在左侧菜单中选择或在顶部搜索栏中搜索日志服务，进入服务控制台内完成开通流程。

### 获取安全凭证

Access Key（访问密钥）是访问火山引擎服务的安全凭证，包含Access Key ID（简称为AK）和Secret Access
Key（简称为SK）两部分。您可登录[火山引擎控制台](https://console.volcengine.com/ )，前往“[访问控制](https://console.volcengine.com/iam )
”的“[访问密钥](https://console.volcengine.com/iam/keymanage/ )”中创建及管理您的Access
Key。更多信息可参考[访问密钥帮助文档](https://www.volcengine.com/docs/6291/65568 )。

## 获取与安装

推荐通过Gradle依赖使用火山引擎SDK for TLS

[![maven](https://img.shields.io/maven-central/v/com.volcengine/volc-tls-android-sdk)](https://search.maven.org/artifact/com.volcengine/volc-tls-android-sdk)




## 相关配置
1. 创建安卓项目。
2. Gradle配置mavenCentral()，并引入SDK。
```xml
   implementation 'com.volcengine:volc-tls-android-sdk:1.1.4'
```
如果有依赖冲突，请使用指定你需要的版本（以okhttp为例子）
```xml
      implementation('com.squareup.okhttp3:okhttp') {
        version {
        strictly("you version")
        }
      }
```
3. Android权限
```xml
   <uses-permission android:name="android.permission.INTERNET" />
```

4. 混淆配置
```xml
   -keep class net.jpountz.** { *; }
```

### SDK使用方法
**方式一**：使用client用于创建project、topic等资源，方法为同步阻塞<br>
[**更多Demo参考**](https://github.com/volcengine/ve-tls-android-sdk/tree/master/src/main/java/com/volcengine/demo)

```java
// 初始化client
ClientConfig clientConfig = new ClientConfig(endPoint, region, accessKey, secretKey, token);
TLSLogClient client = ClientBuilder.newClient(clientConfig);
// 创建日志项目和主题
CreateProjectRequest project = new CreateProjectRequest(projectName, region, description);
CreateProjectResponse createProjectResponse = client.createProject(project);
// 创建日志主题
CreateTopicRequest createTopicRequest = new CreateTopicRequest();
createTopicRequest.setTopicName(topicName);
createTopicRequest.setProjectId(createProjectResponse.getProjectId());
createTopicRequest.setTtl(500);
CreateTopicResponse createTopicResponse = client.createTopic(createTopicRequest);
// 写入日志
List<LogItem> logs = new ArrayList<>();
currentTimeMillis = System.currentTimeMillis();
LogItem item = new LogItem(currentTimeMillis);
item.addContent("index-", "" + 0);
item.addContent("test-key", "test-value");
logs.add(item);
PutLogsRequestV2 putLogsRequestV2 = new PutLogsRequestV2(logs, topicId, null, LZ4, "test-path", "test-file");
PutLogsResponse putLogsResponse = client.putLogsV2(putLogsRequestV2);
```
**方式二**：使用producer写入日志，支持异步非阻塞<br>producer更多[**配置参考**](https://github.com/volcengine/ve-tls-android-sdk/blob/master/src/main/java/com/volcengine/model/tls/producer/Producer.md)
```java
// 初始化producer
Producer producer = ProducerImpl.defaultProducer(
       clientConfig.getEndpoint(), clientConfig.getRegion(), clientConfig.getAccessKeyId(), clientConfig.getAccessKeySecret(),
       clientConfig.getSecurityToken());
producer.start();
// 如果不需要回调，callback参数传null即可
CallBack callBack = new CallBack() {
   @Override
   public void onComplete(Result result) {
        System.out.println("producer result:" + result);
   }
};
LogItem item = new LogItem(System.currentTimeMillis());
item.addContent("test-key", "test-value");
producer.sendLogV2("", topicId, "test-source", "test-file", item, callBack);
```

