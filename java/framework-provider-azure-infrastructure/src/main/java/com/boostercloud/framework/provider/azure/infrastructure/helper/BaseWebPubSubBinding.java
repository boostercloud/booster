package com.boostercloud.framework.provider.azure.infrastructure.helper;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * The bootstrap function of the {@code baseWebPubSubBinding} package ({@code generateBaseWebPubSubFunction()} of
 * {@code function-zip.ts}). Terraform deploys that package with the main function app so that the Functions runtime
 * loads the Web PubSub extension (the trigger's {@code connection} property is what makes it do so) and creates the
 * {@code webpubsub_extension} system key; the real package replaces it right after.
 *
 * <p>The TypeScript handler is {@code async (request, context) => ({ data: 'ok' })}. The Java package needs a class:
 * this one is generated on the fly with the class-file API, targeting Java 8 so any Java worker version can load it,
 * with a single method {@code public String run(String request)} returning {@code {"data":"ok"}}.
 */
public final class BaseWebPubSubBinding {

  /** The function name. */
  public static final String FUNCTION_NAME = "baseWebPubSubBinding";

  /** The fully qualified name of the generated class. */
  public static final String CLASS_NAME = "com.boostercloud.framework.provider.azure.infrastructure.base.BaseWebPubSubBinding";

  /** The method of the generated class. */
  public static final String METHOD_NAME = "run";

  /** The jar with the generated class. */
  public static final String JAR_NAME = "booster-base.jar";

  /** The response of the generated method. */
  public static final String RESPONSE = "{\"data\":\"ok\"}";

  private BaseWebPubSubBinding() {}

  /**
   * @return the {@code entryPoint} of the bootstrap function
   */
  public static String entryPoint() {
    return CLASS_NAME + "." + METHOD_NAME;
  }

  /**
   * @return the class file bytes of the bootstrap class
   */
  public static byte[] classFile() {
    ClassDesc owner = ClassDesc.of(CLASS_NAME);
    return ClassFile.of().build(owner, classBuilder -> {
      classBuilder.withVersion(ClassFile.JAVA_8_VERSION, 0);
      classBuilder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER | ClassFile.ACC_FINAL);
      classBuilder.withSuperclass(ConstantDescs.CD_Object);
      classBuilder.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
          code -> code.aload(0)
              .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
              .return_());
      classBuilder.withMethodBody(METHOD_NAME,
          MethodTypeDesc.of(ConstantDescs.CD_String, ConstantDescs.CD_String), ClassFile.ACC_PUBLIC,
          code -> code.ldc(RESPONSE).areturn());
    });
  }

  /**
   * @return the class file's entry name inside its jar
   */
  public static String classEntryName() {
    return CLASS_NAME.replace('.', '/') + ".class";
  }
}
