# TLS Android Producer Native Re-architecture — Architecture Review

Date: 2026-04-18
Reviewer: Senior Architect
Scope: `2026-04-18-tls-producer-native-c-sdk-unification-design.md`
Reference Baselines:
- SLS C SDK (`aliyun-log-c-sdk`) — 开源版本（不含 persistent）
- SLS Android SDK (`aliyun-log-android-sdk`) — 含 JNI + 内嵌 persistent 版 C SDK 静态库
- ve-tls-c-sdk — 火山引擎 TLS 的 C SDK，含完整 persistent/recover/checkpoint/lease
- ve-tls-android-sdk — 当前火山引擎 Android SDK，含 Java producer 引擎

注：以下参考仓库均已放到当前工作区内，后续实现与核对优先使用这些副本：
- SLS Android SDK：`/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-android-sdk`
- SLS C SDK：`/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/aliyun-log-c-sdk`
- TLS Android SDK：`/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/ve-tls-android-sdk`
- TLS C SDK：`/data00/home/xiayangyang.jacky/workspace/sdk/android_with_producer/ve-tls-c-sdk`

## 审查原则

> **TLS 决定接口语言。SLS 决定成熟 producer 行为基线。C SDK 决定最终实现。**

本审查严格遵循设计文档 §9.1 的原则：
- **API 命名、字段名、函数名以 TLS 风格为准**，不照搬 SLS 命名
- **Producer 行为对齐 SLS**，指异步写入、聚合压缩、持久化 at-least-once、优雅销毁等**运行时行为**，而非 API 形状
- **C SDK 是唯一实现源**，Android facade 只做薄桥接，不重复实现 producer 逻辑

因此，以下审查不要求 TLS Android API 与 SLS Android API 保持命名或签名一致，而是关注：设计是否正确理解了 C SDK 的实际模型，是否在 TLS 风格下完整覆盖了 SLS 已验证的行为基线。

---

## 总体评价

设计方向正确——用 C SDK 替代 Java 引擎、行为对齐 SLS、命名保持 TLS 风格——但在落地细节上存在显著差距。核心问题是设计文档未充分理解 ve-tls-c-sdk 的实际 API 模型（struct 初始化 + 函数式操作 vs. SLS 的 opaque pointer + setter），导致 Config 映射和生命周期管理策略建立在错误假设上。HTTP 客户端方案完全缺失。部分 SLS 行为基线（callback 线程切换、NTP 时间校准、多进程路径隔离）在 C SDK 中无等价实现，需要明确归属。

建议在实施前，先补充一份 **"API Mapping Matrix"**，逐字段列出 TLS Android API → ve-tls-c-sdk API 的映射规则（含单位转换），以及 SLS 行为基线 → C SDK 实现的对应关系。

---

## 处置结论总览

| 编号 | 严重程度 | 问题摘要 | 处置 |
|---|---|---|---|
| R-01 | 🔴 严重 | `ve_tls_config` 是 struct 非 setter 模式 | ✅ 采纳：spec 改为 Java config mirror + 动态更新矩阵 |
| R-02 | 🔴 严重 | `compressType` C SDK 是字符串 | ✅ 采纳：Java enum 映射到 native 字符串 |
| R-03 | 🔴 严重 | destroy 与 C SDK close/destroy 两阶段模型不匹配 | ✅ 采纳：明确 close(timeout) + destroy() 映射和 timeout 规则 |
| R-04 | 🔴 严重 | HTTP 客户端实现方案完全缺失 | ✅ 采纳：Android binding 通过 JNI 调系统 HttpURLConnection 实现 ve_tls_http_client |
| R-05 | 🔴 严重 | `addLogRaw` 语义不匹配 | ✅ 采纳：重定义或第一版不暴露 |
| I-01 | 🟠 重要 | TLS `LogProducerResult` 枚举需自行定义 | ✅ 采纳：定义 TLS 自己的映射表 |
| I-02 | 🟠 重要 | 回调模型需基于 C SDK 能力设计 | ⚡ 部分采纳：保留内部通路，第一版公共 callback 不必全部暴露 |
| I-03 | 🟠 重要 | SLS 行为基线中的 Android 平台特性需明确归属 | ✅ 采纳：逐项标注归属 |
| I-04 | 🟠 重要 | C SDK 配置项需分层 | ✅ 采纳：核心/增值/内部三层 |
| I-05 | 🟠 重要 | `setLogTopic` 与 C SDK 数据模型不匹配 | ✅ 采纳：从当前 spec 删除 |
| G-01 | 🟡 一般 | Platform 抽象层需 Android 适配 | 📝 保留参考 |
| G-02 | 🟡 一般 | 凭证动态更新应利用 credentials_provider | ⚡ 部分采纳：第一版保留 resetSecurityToken 对接 update_static_credentials，provider 作增强项 |
| G-03 | 🟡 一般 | Log Template 优化未纳入 | ❌ 不阻塞：优化项，非架构收敛必需 |
| G-04 | 🟡 一般 | flush/recover 未暴露为公共 API | ⚡ 部分采纳：facade 创建时自动 recover，flush/recover 不必首发公共 API |
| G-05 | 🟡 一般 | 构建集成细节不足 | ✅ 采纳：CMake 集成、so 命名、lz4 集成补到 spec |
| G-06 | 🟡 一般 | `ve_tls_producer_update_endpoint()` API 映射缺失 | ✅ 采纳：动态更新映射进 spec |
| G-07 | 🟡 一般 | 单位转换风险 | ✅ 采纳：映射表加单位列 |

---

## 🔴 严重问题（必须在实施前解决）

### R-01: `ve_tls_config` 是一次性初始化结构体，不是 setter 模式 — 整个 Config 映射策略需要重新设计

**设计文档假设**：`LogProducerConfig` 的 Java setter 可以逐字段桥接到 C SDK 的 config 设置。

**实际情况**：ve-tls-c-sdk 的 `ve_tls_config` 是一个 plain struct，通过 `ve_tls_config_init()` 初始化后直接赋值字段：

```c
// ve-tls-c-sdk 的方式
ve_tls_config config;
ve_tls_config_init(&config);
config.endpoint = "...";
config.region = "...";
config.project_id = "...";
```

而 SLS C SDK 使用的是 opaque pointer + setter function 模式：

```c
// SLS C SDK 的方式
log_producer_config * config = create_log_producer_config();
log_producer_config_set_endpoint(config, "...");
```

**影响**：`ve_tls_producer_create()` 接收的是 `const ve_tls_config *`，一旦 producer 创建，config 就被"冻结"了。后续 Java 层的 `setEndpoint()`、`setRegion()` 等动态配置方法无法通过修改已传入的 config 来生效。C SDK 真正支持的运行时动态更新只有 `ve_tls_producer_update_endpoint()` 和 `ve_tls_producer_update_static_credentials()`。

**处置：✅ 采纳**

Spec 需改为"Java config mirror + 动态更新矩阵"：
- Android facade 维护一份 Java 侧 config 镜像
- Producer 创建前，setter 直接更新镜像字段
- Producer 创建后，setter 更新镜像并调用对应的 C SDK 运行时更新 API
- Spec 中必须明确一张"动态更新矩阵"：哪些 setter 是创建前有效的，哪些支持创建后动态更新，动态更新映射到哪个 C SDK 函数

---

### R-02: `compressType` 类型不匹配 — C SDK 是字符串，设计假设是整数

**设计文档假设**：`setCompressType` 接受整数参数（类似 SLS 的 0/1/2 枚举）。

**实际情况**：ve-tls-c-sdk 的压缩类型是 `const char * compress_type`（字符串类型），而 SLS C SDK 是 `int32_t compressType`（整数类型）。

设计文档第 7.3 节列出了 `setCompressType` 但没有说明参数类型。

**处置：✅ 采纳**

Spec 应改为 Java enum 映射到 native 字符串：

```java
public enum CompressType {
    NONE("none"),
    LZ4("lz4"),
    ZSTD("zstd");
    final String nativeValue;
}
```

JNI 层负责枚举到 C 字符串的映射。Spec 中明确 `setCompressType` 的参数类型和映射规则。

---

### R-03: `destroyLogProducer()` 生命周期与 C SDK 的 close/destroy 两阶段模型不匹配

**设计文档假设**：`destroyLogProducer()` 是单一销毁方法。

**实际情况**：C SDK 有明确的两阶段销毁：
- `ve_tls_producer_close(producer, timeout_ms)` — 优雅关闭，等待缓冲区数据发送完成
- `ve_tls_producer_destroy(producer)` — 释放资源

设计文档将两者合并为 `destroyLogProducer()`，但没有说明：
- close 的 timeout 从哪里来？`setDestroyFlusherWaitSec` 和 `setDestroySenderWaitSec` 在 C SDK 中不存在等价字段（C SDK 的 close 统一使用 `timeout_ms` 参数）
- close 和 destroy 的调用时序如何安排？
- 如果 close 超时后，是否仍然调用 destroy？

**处置：✅ 采纳**

Spec 必须明确 `destroyLogProducer()` 的实现映射：
```
Java destroyLogProducer()
  → 后台线程: ve_tls_producer_close(producer, combined_timeout_ms)
  → 然后: ve_tls_producer_destroy(producer)
```
并明确 timeout 规则：
- `combined_timeout_ms` 的计算方式（建议合并 `destroyFlusherWaitSec` + `destroySenderWaitSec` 为一个 `setCloseTimeoutMs`，直接映射到 `ve_tls_producer_close` 的 `timeout_ms`）
- close 超时后仍调用 destroy（保证资源释放）

---

### R-04: HTTP 客户端实现方案完全缺失 — 这是最关键的架构决策

**设计文档现状**：完全没有讨论 HTTP 客户端方案。

**实际情况**：C SDK 的 `ve_tls_config` 包含 `ve_tls_http_client http_client` 字段，这是一个函数指针结构体：

```c
struct ve_tls_http_client {
    ve_tls_http_do_fn do_request;
    ve_tls_http_free_response_fn free_response;
    void * user_data;
};
```

可选方案：
- **方案 A**：使用 C SDK 的 curl 适配器 — 需要交叉编译 libcurl + OpenSSL/BoringSSL，会显著增大包体积
- **方案 B**：通过 JNI 回调 Java 层的 OkHttp — 引入第三方依赖，违背减负目标
- **方案 C**：Android binding 通过 JNI 调系统 HttpURLConnection/HttpsURLConnection — 不引第三方库

**处置：✅ 采纳，选定方案 C**

优先选择"Android binding 通过 JNI 调系统 HttpURLConnection/HttpsURLConnection 实现 `ve_tls_http_client`"：
- 不引入第三方库（libcurl / OkHttp）
- 包体积最小
- 需在 spec 中明确 JNI HTTP 适配器的实现位置和接口设计

---

### R-05: `addLogRaw` API 语义不匹配

**设计文档列出**：`addLogRaw(byte[][] keys, byte[][] values)`，这是 SLS 的 API 签名（接受 key-value 字节数组对）。

**实际情况**：C SDK 的 raw log API 是：
```c
ve_tls_result ve_tls_producer_add_log_raw(ve_tls_producer * producer, const char * log_buf, size_t log_size, int flush);
```

这接受的是**已序列化的 protobuf buffer**，不是 key-value 对。两者语义完全不同。

**处置：✅ 采纳**

两个选项（spec 需明确选哪个）：
- 选项 A：重定义 `addLogRaw` 签名以匹配 C SDK 的 raw buffer 语义，如 `addLogRaw(byte[] rawBuffer, int offset, int length)`
- 选项 B：第一版不暴露 `addLogRaw`，后续版本再根据需求决定

---

## 🟠 重要问题（应在实施前明确）

### I-01: `LogProducerResult` 枚举值映射未定义

C SDK 的 `ve_tls_result` 只有 6 个值：
```c
VE_TLS_OK=0, VE_TLS_INVALID=1, VE_TLS_DROP_ERROR=2,
VE_TLS_PERSISTENT_ERROR=3, VE_TLS_CLOSED=4, VE_TLS_TIMEOUT=5
```

SLS 的 `LogProducerResult` 有 13 个值。TLS 不需要照搬 SLS 的 13 个枚举，也不能只等于 C SDK 的 6 个——需要利用 `ve_tls_error` 的信息提供合理的错误区分。

**处置：✅ 采纳**

定义 TLS 自己的 result 枚举映射表。Spec 中增加"Error Code Mapping"一节，明确：
- TLS Android 侧暴露哪些 result code
- `ve_tls_result` + `ve_tls_error` 组合如何映射到 TLS result code
- 错误信息（error_message 等）如何传递给 Java 层

---

### I-02: 回调模型差异未充分分析

C SDK 的回调签名：
```c
typedef void (*ve_tls_send_done_fn)(
    ve_tls_result result, size_t log_bytes, size_t compressed_bytes,
    const char * req_id, const char * error_message,
    const unsigned char * raw_buffer, void * user_param,
    int64_t start_id, int64_t end_id);
```

设计文档的回调合约照搬了 SLS 的形状 `onCall(resultCode, reqId, errorMessage, logBytes, compressedBytes)`，但没有说明 C SDK 的 `raw_buffer`、`start_id`/`end_id` 如何处理。

**处置：⚡ 部分采纳**

- C SDK 的 `raw_buffer` / `start_id` / `end_id` 能力要保留**内部通路**（JNI 层和 binding 层需要这些信息来支持退出时数据恢复和持久化确认）
- 但第一版 Android **公共 callback 不必把这些字段全部暴露出去**，可以保持与当前 spec 类似的简洁形状
- Spec 中需明确：内部通路如何使用这些字段，公共 callback 暴露哪些字段

---

### I-03: SLS 行为基线中的 Android 平台特性在 C SDK 中无等价实现

以下 SLS 已验证的 Android 平台行为在 ve-tls-c-sdk 中不存在等价实现，设计文档需要明确每个行为的归属：

| SLS 行为基线 | C SDK 等价 | 归属决策 |
|---|---|---|
| callback 线程模式切换（sender 线程 vs main 线程） | 无 | **Android facade 实现**（类似 SLS 的 ALooper pipe 方案） |
| NTP 时间校准（`setNtpTimeOffset` / `setMaxLogDelayTime` / `setDropDelayLog`） | 无 | **需 spec 明确**：C SDK 补充 or Android facade 实现 or 本阶段不支持 |
| 鉴权失败日志丢弃策略（`setDropUnauthorizedLog`） | 无 | **需 spec 明确**：C SDK 补充 or 本阶段不支持 |
| 多进程持久化路径隔离 | 无 | **Android facade 实现**（路径重写逻辑） |
| HTTP Header 注入（UA 等） | `user_agent` 仅静态 | **需 spec 明确**：是否需要动态注入能力 |
| 网络恢复触发发送 | 无 | **需 spec 明确**：C SDK 补充 or 本阶段不支持 |

**处置：✅ 采纳**

Spec 中逐项标注归属。其中 callback 线程模式切换和多进程路径隔离是 SLS 在 Android 上已验证的关键行为，本阶段必须支持。

---

### I-04: C SDK 的丰富配置项未在设计文档中分层

`ve_tls_config` 有约 50+ 个字段，设计文档只映射了约 20 个（且部分映射有误）。TLS 不需要照搬 SLS 的配置子集，而应该基于 C SDK 的完整能力，定义 TLS 自己的配置分层：

**建议分层**：

| 层级 | 说明 | 示例 |
|---|---|---|
| **核心必暴露** | 创建 producer 的必要参数 + SLS 行为基线所需的参数 | endpoint, region, projectId, topicId, AK/SK, persistent 系列, flush 聚合参数 |
| **TLS 增值暴露** | C SDK 独有、对 TLS 用户有价值的参数 | rate_limit, breaker, retry_policy, credentials_provider, buffer_full_policy |
| **内部不暴露** | 平台/实现细节，Android 用户无需关心 | platform, http_client, tcp_keepalive, http_debug |

未涉及的重要字段：
- **流量控制**：`rate_limit_rps`, `rate_limit_bps`, `breaker_fail_threshold`, `breaker_open_ms`, `breaker_half_open_max_inflight`
- **发送队列**：`send_queue_size`, `send_queue_full_policy`, `send_queue_block_timeout_ms`
- **聚合策略**：`agg_strategy`, `agg_max_log_group_logs`, `agg_max_raw_bytes_per_request`
- **Hash Key 队列**：`key_queue_max_active`, `key_queue_bucket_count`, `key_queue_idle_ttl_ms`, `key_rate_limit_rps`
- **持久化高级配置**：`persistent_max_bytes`, `persistent_max_records`, `persistent_max_segments`, `persistent_high_watermark_pct`, `persistent_low_watermark_pct`, `persistent_overflow_policy`, `persistent_lease_timeout_ms`, `persistent_heartbeat_interval_ms`, `persistent_open_mode`
- **重试策略**：`retry_max_attempts`, `retry_policy`（含指数退避参数）
- **凭证提供者**：`credentials_provider`, `credentials_expire_advance_ms`, `credentials_refresh_min_interval_ms`
- **TLS 验证**：`tls_verify_peer`, `tls_verify_host`, `ca_cert_path`
- **代理**：`proxy`

**处置：✅ 采纳**

Spec 中按三层分层，明确每个 C SDK 配置项的归属。

---

### I-05: `setLogTopic` 与 C SDK 数据模型不匹配

设计文档列出 `setLogTopic`，注释说"reserved for log metadata only if TLS still needs that concept"。但 C SDK 的 `ve_tls_config` 没有 `topic` 或 `log_topic` 字段。它有 `context_flow`（上下文编码）和 `file_name`，这些与 SLS 的 `topic` 是不同概念。

SLS 的 `topic` 是日志元数据字段，会附加到每个 LogGroup 上。TLS 的 `topicId` 是资源标识符（相当于 SLS 的 `logstore`），不是元数据。

**处置：✅ 采纳，从当前 spec 删除**

除非先确认 TLS 真的还需要这个元数据模型，否则 `setLogTopic` 应从当前 spec 删除。如果后续确认需要，再在 C SDK 中添加对应字段并重新引入。

---

## 🟡 一般问题（建议改进）

### G-01: Platform 抽象层需要 Android 适配

C SDK 的 `ve_tls_platform` 结构体定义了文件 I/O、线程、互斥锁等平台抽象接口。当前有 pthread 适配器（`ve_tls_platform_pthread.c`），Android 也使用 pthread，所以大部分可以直接复用。但以下可能需要适配：

- `path_mkdirs` — Android 需要考虑 SELinux 和存储权限
- `file_fsync` — Android 的 `fsync` 在某些文件系统上性能很差
- `time_ms` / `time_unix_ns` — 是否需要 NTP 时间校准（SLS 行为基线）？

**处置：📝 保留参考**

实施时注意，不阻塞 spec 定稿。

---

### G-02: 凭证动态更新机制应利用 C SDK 的 credentials_provider

设计文档列出了 `resetSecurityToken`，但 C SDK 有更丰富的凭证模型：
- `ve_tls_credentials_provider_fn` — 回调式凭证提供者
- `ve_tls_producer_update_static_credentials()` — 运行时更新静态凭证
- `credentials_expire_advance_ms` — 提前刷新时间
- `credentials_refresh_min_interval_ms` — 最小刷新间隔

**处置：⚡ 部分采纳**

第一版策略：
- 保留 `resetSecurityToken()`，内部对接 `ve_tls_producer_update_static_credentials()`
- `credentials_provider` 能力作为增强项，不在首发 spec 中暴露为公共 API
- 后续版本可考虑在 Android facade 中实现 `ve_tls_credentials_provider_fn`，通过 JNI 回调 Java 层获取凭证

---

### G-03: C SDK 的 Log Template 优化未纳入

C SDK 提供了 `ve_tls_log_template` 机制，用于预编译 key schema，减少重复序列化开销。这对高吞吐场景（如 200 条/秒以上）有显著性能优势。

**处置：❌ 不阻塞当前 spec**

`ve_tls_log_template` 是优化项，不是这次架构收敛前的必需项。后续版本可考虑暴露。

---

### G-04: `ve_tls_producer_flush()` 和 `ve_tls_producer_recover()` 未暴露为公共 API

C SDK 提供了显式的 flush 和 recover 操作：
```c
ve_tls_result ve_tls_producer_flush(ve_tls_producer * producer);
ve_tls_result ve_tls_producer_recover(ve_tls_producer * producer);
```

**处置：⚡ 部分采纳**

- `recover()`：如果 Android facade 在创建 producer 时自动调用 `ve_tls_producer_recover()`，那么 recover 不必作为第一版公共 API 暴露。但 spec 需明确"自动 recover"的行为
- `flush()`：作为增强项，不必强行首发。后续版本可考虑暴露

---

### G-05: 构建集成细节不足

设计文档说"Use AGP + CMake externalNativeBuild(cmake)"，但没有说明：
- C SDK 的 `CMakeLists.txt` 如何作为子目录集成？是 `add_subdirectory` 还是预编译静态库？
- Android 特有的 JNI 源码、HTTP 适配器源码放在哪里？如何加入 CMake 构建？
- 最终 shared library 的命名规则是什么？
- 如何处理 C SDK 的第三方依赖（lz4）在 Android 构建中的集成？

**处置：✅ 采纳**

CMake 集成方式、最终 so 命名、lz4 集成方式补到 spec 中。

---

### G-06: 缺少 `ve_tls_producer_update_endpoint()` 的 API 映射

C SDK 支持运行时更新 endpoint/region/topicId：
```c
ve_tls_result ve_tls_producer_update_endpoint(ve_tls_producer * producer, const char * endpoint, const char * region, const char * topic_id);
```

设计文档列出了 `setEndpoint`、`setRegion`、`setProjectId`、`setTopicId` 作为 config setter，但没有说明这些在 producer 创建后调用时如何映射到 `ve_tls_producer_update_endpoint()`。

**处置：✅ 采纳**

动态 endpoint/topicId 更新映射进 spec，作为 R-01 动态更新矩阵的一部分。

---

### G-07: 单位转换风险

设计文档的映射表中存在隐式的单位转换，但没有明确标注：

| Java API | 单位 | C SDK 字段 | 单位 | 转换 |
|---|---|---|---|---|
| `setConnectTimeoutSec` | 秒 | `connect_timeout_ms` | 毫秒 | ×1000 |
| `setSendTimeoutSec` | 秒 | `request_timeout_ms` | 毫秒 | ×1000 |
| `setPacketTimeout` | 毫秒 | `flush_interval_ms` | 毫秒 | 无 |
| `setMaxBufferLimit` | 字节 | `max_buffer_bytes` | 字节 | 无 |

秒→毫秒的转换如果遗漏，会导致超时配置偏差 1000 倍。

**处置：✅ 采纳**

映射表加单位列，JNI 层增加断言验证。TLS 风格下可考虑统一使用毫秒（与 C SDK 一致），避免隐式转换。

---

## 问题汇总

| 编号 | 严重程度 | 问题摘要 | 处置 |
|---|---|---|---|
| R-01 | 🔴 严重 | `ve_tls_config` 是 struct 非 setter 模式 | ✅ 采纳：Java config mirror + 动态更新矩阵 |
| R-02 | 🔴 严重 | `compressType` C SDK 是字符串 | ✅ 采纳：Java enum → native 字符串 |
| R-03 | 🔴 严重 | destroy 与 C SDK close/destroy 两阶段模型不匹配 | ✅ 采纳：明确 close(timeout) + destroy() + timeout 规则 |
| R-04 | 🔴 严重 | HTTP 客户端实现方案完全缺失 | ✅ 采纳：JNI + 系统 HttpURLConnection，不引第三方 |
| R-05 | 🔴 严重 | `addLogRaw` 语义不匹配 | ✅ 采纳：重定义或第一版不暴露 |
| I-01 | 🟠 重要 | TLS `LogProducerResult` 枚举需自行定义 | ✅ 采纳：定义 TLS 映射表 |
| I-02 | 🟠 重要 | 回调模型需基于 C SDK 能力设计 | ⚡ 部分采纳：保留内部通路，第一版公共 callback 不必全部暴露 |
| I-03 | 🟠 重要 | SLS 行为基线中的 Android 平台特性需明确归属 | ✅ 采纳：逐项标注归属 |
| I-04 | 🟠 重要 | C SDK 配置项需分层 | ✅ 采纳：核心/增值/内部三层 |
| I-05 | 🟠 重要 | `setLogTopic` 与 C SDK 数据模型不匹配 | ✅ 采纳：从当前 spec 删除 |
| G-01 | 🟡 一般 | Platform 抽象层需 Android 适配 | 📝 保留参考 |
| G-02 | 🟡 一般 | 凭证动态更新应利用 credentials_provider | ⚡ 部分采纳：第一版 resetSecurityToken 对接 update_static_credentials，provider 作增强项 |
| G-03 | 🟡 一般 | Log Template 优化未纳入 | ❌ 不阻塞：优化项 |
| G-04 | 🟡 一般 | flush/recover 未暴露为公共 API | ⚡ 部分采纳：facade 自动 recover，flush/recover 不必首发公共 API |
| G-05 | 🟡 一般 | 构建集成细节不足 | ✅ 采纳：CMake/so/lz4 补到 spec |
| G-06 | 🟡 一般 | `ve_tls_producer_update_endpoint()` API 映射缺失 | ✅ 采纳：进动态更新矩阵 |
| G-07 | 🟡 一般 | 单位转换风险 | ✅ 采纳：映射表加单位列 |
