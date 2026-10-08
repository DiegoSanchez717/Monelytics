package dev.monelytics;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
  private final TotpService totp = new TotpService("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

  @Test
  void matchesRfc6238Sha1TestVector() {
    assertThat(totp.code("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 1)).isEqualTo("287082");
  }

  @Test
  void encryptsAndRejectsReplayedCode() {
    String secret = totp.createSecret(), encrypted = totp.encrypt(secret);
    assertThat(encrypted).doesNotContain(secret);
    assertThat(totp.decrypt(encrypted)).isEqualTo(secret);
    long step = Instant.now().getEpochSecond() / 30;
    assertThat(totp.verify(encrypted, totp.code(secret, step), -1)).isEqualTo(step);
    assertThat(totp.verify(encrypted, totp.code(secret, step), step)).isEqualTo(-1);
  }

  @Test
  void ciphertextCannotBeTamperedWith() {
    String encrypted = totp.encrypt(totp.createSecret());
    byte[] payload = java.util.Base64.getDecoder().decode(encrypted);
    payload[payload.length - 1] ^= 1;
    assertThatThrownBy(() -> totp.decrypt(java.util.Base64.getEncoder().encodeToString(payload)))
        .isInstanceOf(IllegalStateException.class);
  }
}
