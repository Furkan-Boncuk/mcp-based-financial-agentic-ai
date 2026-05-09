package com.financialagent.auth.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class TestJwtKeys {

  private static final KeyPair KEY_PAIR = generateKeyPair();

  public static String privateKey() {
    return Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
  }

  public static String publicKey() {
    return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
  }

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception exception) {
      throw new IllegalStateException("Test RSA key pair could not be generated", exception);
    }
  }

  private TestJwtKeys() {}
}
