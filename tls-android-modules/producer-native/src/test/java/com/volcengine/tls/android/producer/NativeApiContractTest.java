package com.volcengine.tls.android.producer;

import com.volcengine.tls.android.producer.internal.ConfigSnapshot;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.assertEquals;
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

        Method destroyAsync = bridgeClass.getDeclaredMethod("destroyAsync", long.class, int.class, int.class, int.class, boolean.class);
        assertEquals(void.class, destroyAsync.getReturnType());
    }
}
