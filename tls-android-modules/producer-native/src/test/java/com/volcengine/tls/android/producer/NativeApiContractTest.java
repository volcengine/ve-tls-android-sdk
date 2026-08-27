package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.ConfigSnapshot;
import com.volcengine.tls.android.producer.internal.NativeHttpResponse;
import com.volcengine.tls.android.producer.internal.NativeProducerBridge;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NativeApiContractTest {

    @Test
    public void jniNativeProducerBridge_exposesLifecycleContract() throws Exception {
        Class<?> bridgeClass = Class.forName("com.volcengine.tls.android.producer.internal.JniNativeProducerBridge");
        assertNotNull(bridgeClass);
        assertTrue(Modifier.isPublic(bridgeClass.getModifiers()));

        Method create = bridgeClass.getDeclaredMethod("create", ConfigSnapshot.class, LogProducerCallback.class);
        assertEquals(long.class, create.getReturnType());

        Method nativeCreate = bridgeClass.getDeclaredMethod(
                "nativeCreate",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                boolean.class,
                String.class,
                int.class,
                boolean.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                String[].class,
                String[].class,
                int.class,
                Object.class);
        assertTrue(Modifier.isPrivate(nativeCreate.getModifiers()));
        assertTrue(Modifier.isStatic(nativeCreate.getModifiers()));

        Method updateEndpoint = bridgeClass.getDeclaredMethod(
                "updateEndpoint",
                long.class,
                String.class,
                String.class,
                String.class);
        assertEquals(void.class, updateEndpoint.getReturnType());

        Method resetSecurityToken = bridgeClass.getDeclaredMethod(
                "resetSecurityToken",
                long.class,
                String.class,
                String.class,
                String.class);
        assertEquals(void.class, resetSecurityToken.getReturnType());

        Method destroy = bridgeClass.getDeclaredMethod("destroy", long.class, int.class, int.class, int.class, boolean.class);
        assertEquals(void.class, destroy.getReturnType());
        assertFalse(Arrays.stream(bridgeClass.getDeclaredMethods())
                .anyMatch(method -> "destroyAsync".equals(method.getName())));

        Method bridgeCreate = NativeProducerBridge.class.getMethod("create", ConfigSnapshot.class, LogProducerCallback.class);
        assertEquals(long.class, bridgeCreate.getReturnType());
        assertFalse(Arrays.stream(NativeProducerBridge.class.getMethods())
                .anyMatch(method -> "create".equals(method.getName())
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == LogProducerConfig.class));

        Class<?> snapshotClass = Class.forName("com.volcengine.tls.android.producer.internal.ConfigSnapshot");
        assertFalse(Arrays.stream(snapshotClass.getDeclaredMethods())
                .anyMatch(method -> "toConfig".equals(method.getName())));

        Method compressTypeMapper = bridgeClass.getDeclaredMethod("toNativeCompressType", LogProducerConfig.CompressType.class);
        compressTypeMapper.setAccessible(true);
        assertEquals(1, ((Integer) compressTypeMapper.invoke(null, LogProducerConfig.CompressType.NONE)).intValue());
        assertEquals(2, ((Integer) compressTypeMapper.invoke(null, LogProducerConfig.CompressType.LZ4)).intValue());

        Method durabilityMapper = bridgeClass.getDeclaredMethod(
                "toNativePersistentDurability", LogProducerConfig.PersistentDurability.class);
        durabilityMapper.setAccessible(true);
        assertEquals(1, ((Integer) durabilityMapper.invoke(
                null, LogProducerConfig.PersistentDurability.BUFFERED_WAL)).intValue());
        assertEquals(2, ((Integer) durabilityMapper.invoke(
                null, LogProducerConfig.PersistentDurability.SYNC_WAL)).intValue());

        Method overflowPolicyMapper = bridgeClass.getDeclaredMethod(
                "toNativePersistentOverflowPolicy", LogProducerConfig.PersistentOverflowPolicy.class);
        overflowPolicyMapper.setAccessible(true);
        assertEquals(0, ((Integer) overflowPolicyMapper.invoke(
                null, LogProducerConfig.PersistentOverflowPolicy.REJECT_NEW)).intValue());
        assertEquals(1, ((Integer) overflowPolicyMapper.invoke(
                null, LogProducerConfig.PersistentOverflowPolicy.BLOCK)).intValue());
        assertEquals(2, ((Integer) overflowPolicyMapper.invoke(
                null, LogProducerConfig.PersistentOverflowPolicy.DROP_OLDEST_UNACKED)).intValue());
        assertEquals(3, ((Integer) overflowPolicyMapper.invoke(
                null, LogProducerConfig.PersistentOverflowPolicy.DROP_NEWEST_SAMPLE)).intValue());
    }

    @Test
    public void nativeHttpResponse_doesNotExposeBridgeInternalErrorAccessors() {
        assertFalse(Arrays.stream(NativeHttpResponse.class.getDeclaredMethods())
                .anyMatch(method -> "getErrorCode".equals(method.getName())));
        assertFalse(Arrays.stream(NativeHttpResponse.class.getDeclaredMethods())
                .anyMatch(method -> "getErrorMessage".equals(method.getName())));
    }
}
