package com.volcengine.integration;

import com.alibaba.fastjson.JSON;
import com.google.protobuf.InvalidProtocolBufferException;
import com.volcengine.model.tls.FullTextInfo;
import com.volcengine.model.tls.pb.PutLogRequest;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyUpgradeTest {

  @Test
  void fastjsonPreservesJsonFieldAliases() {
    FullTextInfo original = new FullTextInfo(true, "|", false);

    String json = JSON.toJSONString(original);
    assertTrue(json.contains("\"CaseSensitive\""));
    assertTrue(json.contains("\"Delimiter\""));
    assertTrue(json.contains("\"IncludeChinese\""));

    FullTextInfo parsed = JSON.parseObject(
        "{\"CaseSensitive\":true,\"Delimiter\":\"|\",\"IncludeChinese\":false}",
        FullTextInfo.class);
    assertEquals(original, parsed);
  }

  @Test
  void protobufLitePutLogRequestRoundTrips() throws InvalidProtocolBufferException {
    PutLogRequest.LogContent content = PutLogRequest.LogContent.newBuilder()
        .setKey("message")
        .setValue("dependency-upgrade")
        .build();
    PutLogRequest.Log log = PutLogRequest.Log.newBuilder()
        .setTime(1_700_000_000_000L)
        .addContents(content)
        .build();
    PutLogRequest.LogGroup group = PutLogRequest.LogGroup.newBuilder()
        .setFileName("security-regression.log")
        .setSource("integration-test")
        .addLogs(log)
        .build();
    PutLogRequest.LogGroupList original = PutLogRequest.LogGroupList.newBuilder()
        .addLogGroups(group)
        .build();

    byte[] serialized = original.toByteArray();
    PutLogRequest.LogGroupList parsed = PutLogRequest.LogGroupList.parseFrom(serialized);

    assertEquals(original, parsed);
    assertEquals("dependency-upgrade", parsed.getLogGroups(0).getLogs(0).getContents(0).getValue());
  }

  @Test
  void lz4SafeInstanceRoundTripsWithoutJni() {
    byte[] original = "safe Java LZ4 dependency upgrade regression payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    LZ4Factory factory = LZ4Factory.safeInstance();
    LZ4Compressor compressor = factory.fastCompressor();
    LZ4SafeDecompressor decompressor = factory.safeDecompressor();

    byte[] compressed = new byte[compressor.maxCompressedLength(original.length)];
    int compressedLength = compressor.compress(original, 0, original.length, compressed, 0, compressed.length);
    byte[] restored = new byte[original.length];
    int restoredLength = decompressor.decompress(compressed, 0, compressedLength, restored, 0, restored.length);

    assertArrayEquals(original, Arrays.copyOf(restored, restoredLength));
  }
}
