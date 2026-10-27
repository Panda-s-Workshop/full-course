package com.panda.fullcourse.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The serving lookup on a class whose other methods name a type that doesn't exist, as a block with a
 * {@code PoseStack} overlay method looks on a dedicated server: listing its methods throws, finding the one by name
 * and type doesn't.
 */
class ServedFromTest {
    private static final ClassDesc MISSING = ClassDesc.of("absent.client.PoseStack");
    private static final ClassDesc STRING = ConstantDescs.CD_String;

    /** A public class {@code fixture.RailwayBlock} with {@code overlay(PoseStack)} and {@code getServingItem(String)}. */
    private static Class<?> railwayBlock() throws Exception {
        ClassDesc self = ClassDesc.of("fixture.RailwayBlock");
        byte[] bytes = ClassFile.of().build(self, cb -> {
            cb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            cb.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                    .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_());
            cb.withMethodBody("overlay", MethodTypeDesc.of(ConstantDescs.CD_void, MISSING), ClassFile.ACC_PUBLIC, code -> code.return_());
            cb.withMethodBody("getServingItem", MethodTypeDesc.of(STRING, STRING), ClassFile.ACC_PUBLIC,
                    code -> code.ldc("slice of " ).aload(1).invokevirtual(STRING, "concat", MethodTypeDesc.of(STRING, STRING)).areturn());
        });
        ClassLoader loader = new ClassLoader(ServedFromTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.equals("fixture.RailwayBlock")) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return loader.loadClass("fixture.RailwayBlock");
    }

    @Test
    void listingTheMethodsOfABlockWithAClientOnlySignatureThrows() throws Exception {
        Class<?> type = railwayBlock();
        // what 1.0.0-beta.1 did, and what crashed the server
        assertThrows(NoClassDefFoundError.class, type::getMethods);
        assertThrows(NoClassDefFoundError.class, () -> type.getMethod("getServingItem", String.class));
    }

    @Test
    void theServingMethodIsStillFoundByNameAndType() throws Throwable {
        Class<?> type = railwayBlock();
        Optional<MethodHandle> handle = ServedFrom.find(type, "getServingItem", MethodType.methodType(String.class, String.class));
        assertTrue(handle.isPresent(), "getServingItem(String) should be found without resolving overlay(PoseStack)");
        assertEquals("slice of pie", handle.get().invoke(type.getConstructor().newInstance(), "pie"));
    }

    @Test
    void aMethodTheClassDoesntHaveIsEmptyNotAnError() throws Exception {
        Class<?> type = railwayBlock();
        assertEquals(Optional.empty(), ServedFrom.find(type, "getPieSliceItem", MethodType.methodType(String.class)));
        assertEquals(Optional.empty(), ServedFrom.find(type, "getServingItem", MethodType.methodType(String.class, Integer.class)));
    }
}
