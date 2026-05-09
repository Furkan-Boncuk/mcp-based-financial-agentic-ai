package com.financialagent.auth.service;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenHasher {

  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final String STORAGE_PREFIX = "pbkdf2_sha256";
  private static final int ITERATIONS = 310_000;
  private static final int SALT_BYTES = 16;
  private static final int KEY_LENGTH_BITS = 256;

  private final SecureRandom secureRandom;

  public TokenHasher(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String hash(byte[] rawSecret) {
    validateRawSecret(rawSecret);

    byte[] salt = new byte[SALT_BYTES];
    secureRandom.nextBytes(salt);
    byte[] hash = pbkdf2(rawSecret, salt, ITERATIONS);

    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return String.join(
        "$",
        STORAGE_PREFIX,
        String.valueOf(ITERATIONS),
        encoder.encodeToString(salt),
        encoder.encodeToString(hash));
  }

  public boolean matches(byte[] rawSecret, String storedHash) {
    validateRawSecret(rawSecret);
    ParsedHash parsedHash = parseStoredHash(storedHash);
    byte[] candidate = pbkdf2(rawSecret, parsedHash.salt(), parsedHash.iterations());
    return MessageDigest.isEqual(candidate, parsedHash.hash());
  }

  private byte[] pbkdf2(byte[] rawSecret, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(toChars(rawSecret), salt, iterations, KEY_LENGTH_BITS);
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
      throw new ServiceException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Token hash operation could not be completed");
    }
  }

  private ParsedHash parseStoredHash(String storedHash) {
    if (storedHash == null || storedHash.isBlank()) {
      throw invalidStoredHash();
    }

    String[] parts = storedHash.split("\\$", -1);
    if (parts.length != 4 || !STORAGE_PREFIX.equals(parts[0])) {
      throw invalidStoredHash();
    }

    try {
      int iterations = Integer.parseInt(parts[1]);
      Base64.Decoder decoder = Base64.getUrlDecoder();
      byte[] salt = decoder.decode(parts[2]);
      byte[] hash = decoder.decode(parts[3]);
      if (iterations <= 0 || salt.length == 0 || hash.length == 0) {
        throw invalidStoredHash();
      }
      return new ParsedHash(iterations, salt, hash);
    } catch (IllegalArgumentException exception) {
      throw invalidStoredHash();
    }
  }

  private void validateRawSecret(byte[] rawSecret) {
    if (rawSecret == null || rawSecret.length == 0) {
      throw new ServiceException(ErrorCode.AUTH_REFRESH_REVOKED, "Invalid refresh token secret");
    }
  }

  private char[] toChars(byte[] rawSecret) {
    char[] chars = new char[rawSecret.length];
    for (int index = 0; index < rawSecret.length; index++) {
      chars[index] = (char) (rawSecret[index] & 0xff);
    }
    return chars;
  }

  private ServiceException invalidStoredHash() {
    return new ServiceException(ErrorCode.AUTH_REFRESH_REVOKED, "Invalid refresh token hash");
  }

  private record ParsedHash(int iterations, byte[] salt, byte[] hash) {}
}
