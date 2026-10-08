package dev.wealthpath;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class TotpService {
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  private final SecureRandom random = new SecureRandom();
  private final SecretKeySpec key;

  TotpService(@Value("${wealthpath.mfa-encryption-key}") String encodedKey) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encodedKey);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("MFA_ENCRYPTION_KEY must be base64 encoded 32 random bytes.");
    }
    if (decoded.length != 32)
      throw new IllegalStateException("MFA_ENCRYPTION_KEY must contain 32 bytes.");
    key = new SecretKeySpec(decoded, "AES");
  }

  String createSecret() {
    byte[] raw = new byte[20];
    random.nextBytes(raw);
    return base32(raw);
  }

  String encrypt(String secret) {
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(nonce.length + encrypted.length)
                  .put(nonce)
                  .put(encrypted)
                  .array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to encrypt MFA secret", e);
    }
  }

  String decrypt(String stored) {
    try {
      byte[] payload = Base64.getDecoder().decode(stored);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(payload, 0, 12)));
      return new String(
          cipher.doFinal(Arrays.copyOfRange(payload, 12, payload.length)),
          StandardCharsets.US_ASCII);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Unable to decrypt MFA secret; verify encryption key", e);
    }
  }

  long verify(String encryptedSecret, String code, long lastStep) {
    if (code == null || !code.matches("[0-9]{6}")) return -1;
    long now = Instant.now().getEpochSecond() / 30;
    String secret = decrypt(encryptedSecret);
    for (long step = now - 1; step <= now + 1; step++)
      if (step > lastStep
          && MessageDigest.isEqual(
              code.getBytes(StandardCharsets.US_ASCII),
              code(secret, step).getBytes(StandardCharsets.US_ASCII))) return step;
    return -1;
  }

  // RFC 6238 TOTP uses a 30-second counter; comparison above is constant time and prevents code
  // reuse.
  String code(String secret, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int offset = hash[19] & 15;
      int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
      return String.format(Locale.ROOT, "%06d", binary % 1000000);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private String base32(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    int buffer = 0, bits = 0;
    for (byte value : bytes) {
      buffer = (buffer << 8) | (value & 255);
      bits += 8;
      while (bits >= 5) {
        result.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
        bits -= 5;
      }
    }
    if (bits > 0) result.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
    return result.toString();
  }

  private byte[] unbase32(String text) {
    ByteBuffer result = ByteBuffer.allocate(text.length() * 5 / 8);
    int buffer = 0, bits = 0;
    for (char value : text.toCharArray()) {
      int digit = ALPHABET.indexOf(value);
      if (digit < 0) throw new IllegalArgumentException("Invalid base32");
      buffer = (buffer << 5) | digit;
      bits += 5;
      if (bits >= 8) {
        result.put((byte) (buffer >> (bits - 8)));
        bits -= 8;
      }
    }
    return result.array();
  }
}
