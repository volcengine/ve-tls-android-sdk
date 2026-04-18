# TLS Android Producer Native Re-architecture — SLS 行为对齐审查

Date: 2026-04-18
Reviewer: Senior Architect
Scope: `2026-04-18-tls-producer-native-c-sdk-unification-design.md`
Reference Baselines:
- SLS C SDK (`aliyun-log-c-sdk` persistent 分支) — 含完整 persistent/recover 能力
- SLS Android SDK (`aliyun-log-android-sdk`) — 含 JNI + 内嵌 persistent 版 C SDK 静态库

注：以下代码引用均已切换到当前工作区内副本：
- Android：`/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk`
- C SDK：`/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk`

---

## 审查原则

> **TLS 决定接口语言。SLS 决定成熟 producer 行为基线。C SDK 决定最终实现。**

本审查严格遵循设计文档 §9.1 的原则：
- **API 命名、字段名、函数名以 TLS 风格为准**，不照搬 SLS 命名
- **Producer 行为对齐 SLS**，指异步写入、聚合压缩、持久化 at-least-once、优雅销毁等**运行时行为**，而非 API 形状
- **C SDK 是唯一实现源**，Android facade 只做薄桥接，不重复实现 producer 逻辑

因此，本审查不要求 TLS Android API 与 SLS Android API 保持命名或签名一致，而是关注：**在 TLS 风格下，TLS 的 producer 行为基线是否与 SLS 已验证的行为对齐**。

---

## 总体评价

设计方向正确——用 C SDK 替代 Java 引擎、行为对齐 SLS、命名保持 TLS 风格——但在 **"哪些行为需要与 SLS 对齐"** 这个核心问题上，设计文档存在显著差距。

设计文档假设 SLS C SDK 的 persistent 分支提供了所有需要对齐的行为基线，但实际情况是：**SLS Android SDK 的很多关键行为是在 Java facade 和 JNI 层实现的，而非 C SDK 本身**。设计文档需要明确区分"哪些行为 C SDK 已提供"、"哪些行为需要 Android facade 补充实现"。

---

## ✅ 已正确对齐的部分

| SLS 行为基线 | TLS 设计文档对应章节 | 状态 |
|---|---|---|
| 异步写入路径 | §9.2 | ✅ 对齐 |
| 聚合与压缩（LZ4 默认） | §9.2 | ✅ 对齐 |
| 持久化 at-least-once 语义 | §9.2 | ✅ 对齐（C SDK 已实现） |
| 优雅销毁（有界等待） | §11.5 | ✅ 对齐 |
| 多进程持久化路径隔离 | §11.2 | ✅ 对齐（Android facade 实现） |
| Callback 线程模式切换 | §11.3 | ✅ 对齐（Android facade 实现） |
| 配置项到 C SDK 的映射 | §8 | ✅ 基本对齐 |
| Persistent 系列 API | §7.3 | ✅ 对齐（C SDK persistent 分支已实现） |
| `callbackFromSenderThread` 字段 | §7.3、§8 | ✅ 对齐（C SDK persistent 分支第 76 行有此字段） |

---

## 🔴 关键问题：行为不对齐

### A-01: 默认配置值未明确采用哪一套

这是 TLS 设计文档中最严重的问题——没有明确 TLS 应该采用哪一套默认值，而 C SDK 默认值与 SLS Android SDK 重写默认值存在显著差异：

| 参数 | SLS C SDK 默认值 | SLS Android 重写值 | 差异影响 |
|------|-----------------|-------------------|---------|
| `logBytesPerPackage` | **3MB** | **1MB** | Android 重写为更保守的移动端值 |
| `logCountPerPackage` | **2048** | **1024** | 同上 |
| `sendThreadCount` | **0**（flusher 负责发送） | **1** | Android 强制使用独立发送线程 |
| `dropDelayLog` | **1**（丢弃过期日志） | **0**（改写时间为当前时间） | Android 偏向保留数据 |
| `maxLogDelayTime` | **7天** | 未设置（保持 C SDK 默认） | - |
| `callbackFromSenderThread` | **1**（sender 线程回调） | 未显式设置（依赖 C SDK 默认） | - |

SLS C SDK 默认值见 [log_producer_config.c:11-33](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.c#L11-L33)。
SLS Android SDK 重写默认值见 [LogProducerConfig.java:66-73](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/java/com/aliyun/sls/android/producer/LogProducerConfig.java#L66-L73)。

**影响**：如果 TLS 直接采用 C SDK 默认值而不重写，则在 Android 设备上：
- 更大的聚合包（3MB vs 1MB）可能导致更多内存压力
- `sendThreadCount=0` 意味着 flusher 线程同时负责聚合和发送，在网络抖动时可能阻塞聚合

**建议**：TLS 设计文档应明确声明：
1. 采用 C SDK 原生默认值（不重写）
2. 采用 SLS Android SDK 的重写默认值（偏向移动端）
3. 混合策略（部分重写）

如果选择方案 2 或 3，需要在文档中列出每个需要重写的参数及其理由。

---

### A-02: `isValid()` 的 SLS 行为与直觉不符

SLS C SDK 的 `log_producer_config_is_valid` [第393-424行](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.c#L393-L424)：

```c
int log_producer_config_is_valid(log_producer_config * config) {
    if (config == NULL) {
        aos_error_log("invalid producer config");
        return 0;
    }
    if (config->endpoint == NULL || config->project == NULL || config->logstore == NULL) {
        aos_error_log("invalid producer config destination params");
        // return 0;  <-- 被注释掉了！
    }
    if (config->accessKey == NULL || config->accessKeyId == NULL) {
        aos_error_log("invalid producer config authority params");
        // return 0;  <-- 被注释掉了！
    }
    if (config->packageTimeoutInMS < 0 || ...) {
        aos_error_log("invalid producer config log merge and buffer params");
        return 0;
    }
    if (config->usePersistent) {
        if (config->persistentFilePath == NULL || ...) {
            aos_error_log("invalid producer persistent config params");
            return 0;
        }
    }
    return 1;  // 即使 endpoint/project/logstore/accessKey 全部为 NULL，仍然返回 1
}
```

**这意味着 SLS 的 `isValid()` 即使 endpoint、project、logstore、accessKey 全部为 NULL，仍然返回 1（valid）！** 只记录错误日志但不阻止使用。

TLS 设计文档在 §7.3 列出了 `isValid` 和 `isEnabled`，用户可能期望 `isValid` 会严格校验必填参数。

**建议**：TLS 需要明确决定：
- 保持与 SLS 行为完全一致（即 `isValid` 只校验数值范围，不校验必填参数）
- 修正 SLS 的行为（即 `isValid` 严格校验必填参数）

如果选择修正，应在文档中标注为"行为修正"，并说明理由。

---

### A-03: `setEndpoint` 的静默失败行为需要 Android facade 补偿

SLS C SDK [log_producer_config.c:312-332](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.c#L312-L332)：

```c
void log_producer_config_set_endpoint(log_producer_config * config, const char * endpoint) {
    if (!endpoint) {
        _copy_config_string(NULL, &config->endpoint);
        return;
    }
    if (strlen(endpoint) < 8) {
        return;  // 静默返回！
    }
    if (strncmp(endpoint, "http://", 7) == 0) {
        endpoint += 7;
    } else if (strncmp(endpoint, "https://", 8) == 0) {
        config->using_https = 1;
        endpoint += 8;
    }
    _copy_config_string(endpoint, &config->endpoint);
}
```

如果 endpoint 长度 < 8 字符，C SDK **静默失败**，不设置 endpoint 也不报错。后续 `isValid()` 检查也会因为注释掉的 `return 0` 而通过。

这会导致用户调用 `setEndpoint("abc")` 后，后续所有日志发送都会失败（因为没有 endpoint），但没有任何报错提示。

**建议**：TLS Android facade 应在 JNI 调用前进行 endpoint 的 validation，或在 Android 层抛出异常/日志警告。这是典型的"Android facade 补偿 C SDK 不良行为"的场景。

---

### A-04: `addLogRaw` 缺少 flush 参数，与 SLS 行为不一致

SLS Android SDK 的 `addLogRaw` 实现 [LogProducerClient.java:73-81](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/java/com/aliyun/sls/android/producer/LogProducerClient.java#L73-L81)：

```java
public LogProducerResult addLogRaw(byte[][] keys, byte[][] values) {
    // ...
    int res = log_producer_client_add_log_with_len_time_int32(client, logTime, keys.length, keys, values);
    // flush 硬编码为 0
}
```

JNI 层 [LogProducerClient.c:266](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/cpp/com_aliyun_sls_android_producer_LogProducerClient.c#L266)：
```c
int res = log_producer_client_add_log_with_len_time_int32(..., 0);  // flush 硬编码为 0
```

而 TLS 设计文档在 §7.4 列出了 `addLog(Log, int flush)`，但 **`addLogRaw` 没有 flush 参数**。

**建议**：TLS 应保持一致性，为 `addLogRaw` 添加可选的 flush 参数，或者在设计文档中说明为什么 `addLogRaw` 不需要 flush。

---

### A-05: 环境初始化处理策略未明确

Persistent 分支的 [log_producer_client.h:32](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_client.h#L32)：

```c
LOG_EXPORT log_producer_result log_producer_env_init();  // 无参数
```

**去掉了**非 persistent 分支中的 `log_global_flag` 参数，也**去掉了** `log_producer_global_send_thread_init` API。

TLS 设计文档在 §7.4 `LogProducerClient` 生命周期中没有提到环境初始化。

**建议**：TLS 应在设计文档中明确：
- 是否在 `LogProducerClient` 构造函数中自动调用 `log_producer_env_init()`
- 是否需要处理多次初始化的幂等性
- 是否需要在应用退出时调用 `log_producer_env_destroy()`

---

### A-06: C SDK persistent 分支没有 `region` 字段

Persistent 分支的 `log_producer_config` 结构体 [第28-84行](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.h#L28-L84) **没有 `region` 字段**。

TLS 设计文档在 §7.3 中列出了 `setRegion`，在 §8 中映射到 `region -> region`。

**如果 TLS 的 C SDK 使用的是与 SLS 相同的 persistent 分支，这个字段无法映射到 C SDK。**

**建议**：TLS 需要确认：
1. TLS C SDK persistent 分支是否包含 `region` 字段（如果 TLS 在 SLS 基础上做了修改）
2. 如果没有，TLS Android facade 需要消化掉这个参数（例如将 region 拼接到 endpoint 中），或者从 API 中移除 `setRegion`

---

### A-07: ZSTD 压缩不被 C SDK persistent 分支支持

SLS Android SDK [LogProducerConfig.java:400-408](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/java/com/aliyun/sls/android/producer/LogProducerConfig.java#L400-L408)：
```java
public enum CompressType {
    LZ4(1),
    ZSTD(2);
}
```

但 C SDK persistent 分支 [log_producer_config.c:262-269](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.c#L262-L269)：
```c
void log_producer_config_set_compress_type(log_producer_config * config, int32_t compress_type) {
    if (config == NULL || compress_type < 0 || compress_type > 1) {
        return;  // 只接受 0 或 1
    }
    config->compressType = compress_type;
}
```

**如果用户传入 ZSTD(2)，C SDK 会静默忽略，不设置压缩类型也不报错。**

**建议**：TLS 设计文档应明确：
- 是否只支持 LZ4（与 C SDK persistent 分支一致）
- 如果支持 ZSTD，需要在 C SDK 中添加 zstd 支持

---

## 🟡 中等问题

### B-01: `hashKey` 与 `mode`/`shardKey` 的关系不明确

TLS 设计文档在 §7.3 列出了 `setHashKey`，在 §8 映射到 "default hash_key or per-log hash key"。

C SDK persistent 分支有 `mode` 和 `shardKey` 字段 [log_producer_config.h:79-80](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.h#L79-L80)，以及 `set_mode` 和 `set_shardkey` 函数：

```c
int32_t mode; // 0, LoadBalance; 1, KeyShard
char *shardKey;
```

TLS 设计中只列出了 `setHashKey`，没有提到 `mode`（LoadBalance vs KeyShard）。

**建议**：TLS 需要明确：
- `setHashKey` 是否隐式将 mode 设置为 KeyShard？
- 还是 `setHashKey` 只是设置默认的 shardKey，不改变 mode？
- 是否需要暴露 `setMode` 让用户明确选择？

---

### B-02: TLS 缺少 `setUseWebtracking` 的处理

C SDK persistent 分支有 `webTracking` 字段和 `log_producer_config_set_use_webtracking` 函数 [log_producer_config.h:77、第348行](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.h#L77)。

TLS 设计文档没有列出这个方法。

**建议**：如果 TLS 不需要 webtracking 功能，应在 §3 Non-goals 中明确说明。

---

### B-03: SLS JNI 存在的内存泄漏问题 TLS 需要规避

在 SLS Android SDK 的 JNI 实现中：

1. **`g_time_func` 永不释放** — [LogProducerConfig.c:401](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/cpp/com_aliyun_sls_android_producer_LogProducerConfig.c#L401)：
   ```c
   g_time_func = (*env)->NewGlobalRef(env, func);  // 永不 ReleaseGlobalRef
   ```

2. **`usr_params` 和 `c_callback` 在 destroy 时未释放** — [LogProducerClient.c:155-156](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/cpp/com_aliyun_sls_android_producer_LogProducerClient.c#L155-L156)：
   ```c
   jobject c_callback = (*env)->NewGlobalRef(env, callback);
   user_params *usr_params = (user_params *)malloc(sizeof(user_params));
   // destroy_log_producer 中没有对应的 DeleteGlobalRef 和 free
   ```

3. **`main_thread_looper` 和 pipe 永不关闭** — [LogProducerClient.c:129-139](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/cpp/com_aliyun_sls_android_producer_LogProducerClient.c#L129-L139)：
   ```c
   static void init_main_looper_pipe() {
       pipe(message_pipe);
       ALooper_addFd(main_thread_looper, message_pipe[0], ...);
       // 没有对应的 pipe close 和 ALooper_removeFd
   }
   ```

**建议**：TLS 的 JNI 实现必须正确处理这些资源的生命周期管理。这不是与 SLS 对齐的问题，而是 TLS 需要规避 SLS 的设计缺陷。

---

### B-04: `setMaxBufferLimit` 的 JNI 类型问题

C SDK [log_producer_config.h:200](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_config.h#L200)：
```c
LOG_EXPORT void log_producer_config_set_max_buffer_limit(log_producer_config * config, int64_t max_buffer_bytes);
```

但 SLS Android SDK 的 JNI 绑定 [LogProducerConfig.c:218-221](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/cpp/com_aliyun_sls_android_producer_LogProducerConfig.c#L218-L221) 使用了 `jint`（32位）：
```c
JNIEXPORT void JNICALL
Java_..._set_1max_1buffer_1limit(JNIEnv *env, jclass obj, jlong config, jint num)
```

C SDK 默认值是 64MB，在 jint 范围内。但如果 TLS 希望支持超过 2GB 的 buffer，这个 JNI 签名需要修复。

**建议**：TLS 的 JNI 绑定应使用 `jlong` 来映射 `int64_t`，避免潜在的类型截断。

---

### B-05: SLS 的 `log_producer_client_add_log_with_len` 接受 `size_t *` 长度，但 Android JNI 传入 `int32_t *`

C SDK [log_producer_client.h:100](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk/src/log_producer_client.h#L100)：
```c
LOG_EXPORT log_producer_result log_producer_client_add_log_with_len(
    log_producer_client * client, int32_t pair_count,
    char ** keys, size_t * key_lens, char ** values, size_t * value_lens, int flush);
```

但 SLS Android JNI [LogProducerClient.c:191](file:///data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk/aliyun_sls_android_producer/src/main/cpp/com_aliyun_sls_android_producer_LogProducerClient.c#L191) 使用 `int32_t *`：
```c
int32_t *c_key_lens = (int32_t *) malloc(len_keys * sizeof(int32_t));
```

在 64 位系统上 `size_t` 是 64 位，`int32_t` 是 32 位，类型不匹配可能导致问题。

**建议**：TLS 的 JNI 实现应使用正确的 `size_t` 类型来调用 C SDK。

---

## 📋 问题汇总表

| 编号 | 严重程度 | 问题摘要 | 与 SLS 对齐状态 | 建议 |
|------|----------|---------|----------------|------|
| A-01 | 🔴 关键 | 默认配置值未明确采用哪一套 | ⚠️ C SDK 与 Android 有差异 | 明确采用 C SDK 原生值还是 Android 重写值 |
| A-02 | 🔴 关键 | `isValid()` 行为与直觉不符 | ❌ 需决定是否修正 | 明确是否保持兼容或修正 |
| A-03 | 🔴 关键 | `setEndpoint` 静默失败 | ❌ 需 facade 补偿 | Android 层增加 validation |
| A-04 | 🔴 关键 | `addLogRaw` 缺少 flush 参数 | ⚠️ 与 SLS 不一致 | 保持 API 一致性 |
| A-05 | 🔴 关键 | 环境初始化处理策略未明确 | ❌ 缺失 | 明确初始化策略 |
| A-06 | 🔴 关键 | `region` 字段在 C SDK 中不存在 | ❌ 无法映射 | 确认 TLS C SDK 是否有此字段 |
| A-07 | 🔴 关键 | ZSTD 压缩不被 C SDK 支持 | ⚠️ 会静默失败 | 明确只支持 LZ4 或扩展 C SDK |
| B-01 | 🟡 中等 | `hashKey` 与 `mode` 关系不明确 | ⚠️ 不明确 | 明确与 mode 的关系 |
| B-02 | 🟡 中等 | 缺少 `setUseWebtracking` | ⚠️ 缺失 | 在 Non-goals 中说明 |
| B-03 | 🟡 中等 | JNI 内存泄漏问题 | ❌ TLS 需规避 SLS 缺陷 | 正确管理 JNI 资源生命周期 |
| B-04 | 🟡 中等 | `setMaxBufferLimit` JNI 类型问题 | ⚠️ 潜在截断 | 使用 `jlong` 映射 `int64_t` |
| B-05 | 🟡 中等 | `addLog` 长度类型不匹配 | ⚠️ 潜在问题 | 使用 `size_t` 调用 C SDK |

---

## 建议增加的文档内容

建议在设计文档中增加一个 **"SLS 行为对齐矩阵"** 章节，格式如下：

```markdown
## SLS 行为对齐矩阵

| SLS 行为 | TLS 是否对齐 | 对齐方式 | 差异说明 |
|---|---|---|---|
| 异步写入 | 是 | C SDK 实现 | - |
| 聚合压缩（LZ4 默认） | 是 | C SDK 实现 | - |
| 持久化 at-least-once | 是 | C SDK 实现 | - |
| 默认 logBytesPerPackage | 待确认 | 需明确 | C SDK 3MB vs Android 1MB |
| 默认 logCountPerPackage | 待确认 | 需明确 | C SDK 2048 vs Android 1024 |
| 默认 sendThreadCount | 待确认 | 需明确 | C SDK 0 vs Android 1 |
| 默认 dropDelayLog | 待确认 | 需明确 | C SDK 丢弃 vs Android 改写时间 |
| isValid() 行为 | 待确认 | 需明确 | SLS 不校验必填参数 |
| setEndpoint 静默失败 | 否 | Android facade 补偿 | 增加 validation |
| ... | ... | ... | ... |
```

---

## 结论

TLS 设计文档的整体方向正确，但在 **默认值策略、C SDK 能力边界、Android facade 补偿范围** 三个核心问题上需要明确。建议在实施前：

1. **明确默认值策略** — 采用 C SDK 原生值还是 Android 重写值
2. **确认 TLS C SDK 的完整能力** — 是否有 `region`、是否有 ZSTD、是否有 webtracking 等
3. **增加 SLS 行为对齐矩阵** — 逐项标注哪些对齐、哪些不对齐、哪些需要补偿
