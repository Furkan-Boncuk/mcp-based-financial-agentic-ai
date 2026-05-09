package com.financialagent.auth.service;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.config.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JwtKeyService {

  private final JwtProperties properties;
  private RSAKey signingKey;
  private RSAKey publicJwk;

  public JwtKeyService(JwtProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  void initialize() {
    try {
      RSAPrivateKey privateKey = parsePrivateKey(properties.privateKey());
      RSAPublicKey publicKey = parsePublicKey(properties.publicKey());
      signingKey =
          new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(properties.keyId()).build();
      publicJwk = signingKey.toPublicJWK();
    } catch (RuntimeException exception) {
      if (exception instanceof ServiceException serviceException) {
        throw serviceException;
      }
      throw invalidKeyConfiguration();
    }
  }

  public RSAKey signingKey() {
    return signingKey;
  }

  public Map<String, Object> jwks() {
    return new JWKSet(publicJwk).toJSONObject();
  }

  private RSAPrivateKey parsePrivateKey(String value) {
    if (value == null || value.isBlank()) {
      throw invalidKeyConfiguration();
    }

    try {
      byte[] keyBytes = decodeKey(value);
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    } catch (Exception exception) {
      throw invalidKeyConfiguration();
    }
  }

  private RSAPublicKey parsePublicKey(String value) {
    if (value == null || value.isBlank()) {
      throw invalidKeyConfiguration();
    }

    try {
      byte[] keyBytes = decodeKey(value);
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    } catch (Exception exception) {
      throw invalidKeyConfiguration();
    }
  }

  private byte[] decodeKey(String value) {
    String normalized =
        value
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(normalized);
  }

  private ServiceException invalidKeyConfiguration() {
    return new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "Invalid JWT key configuration");
  }
}
