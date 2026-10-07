package com.example.payment.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class PepperedPasswordEncoderTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final PepperedPasswordEncoder encoder = new PepperedPasswordEncoder(KEY, 10);
    @Test void differentSaltsBothVerifyWithoutDoubleHashing() {
        String first = encoder.encode(" mật khẩu 😀 "); String second = encoder.encode(" mật khẩu 😀 ");
        assertThat(first).startsWith(PepperedPasswordEncoder.PREFIX + "$2b$10$").isNotEqualTo(second);
        assertThat(encoder.matches(" mật khẩu 😀 ", first)).isTrue();
        assertThat(encoder.matches(" mật khẩu 😀 ", second)).isTrue();
        assertThat(encoder.matches("mật khẩu 😀", first)).isFalse();
        assertThat(encoder.matches(" MẬT KHẨU 😀 ", first)).isFalse();
    }
    @Test void supportsFull128CodePointsAndDoesNotIgnoreSuffixBeyond72Bytes() {
        String password = "😀".repeat(128);
        assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
        String prefix = "a".repeat(72);
        assertThat(encoder.matches(prefix + "y", encoder.encode(prefix + "x"))).isFalse();
        assertThat(encoder.matches("12345678", encoder.encode("12345678"))).isTrue();
        for (String invalid : new String[]{"1234567", "😀".repeat(129), "12345678\uD800"}) {
            assertThatThrownBy(() -> encoder.encode(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rejectsUnrecognizedOrMalformedHashesAndWrongKey() {
        String hash = encoder.encode("correct password");
        assertThat(new PepperedPasswordEncoder(KEY, 12).matches("correct password", hash)).isTrue();
        byte[] anotherKey = new byte[32]; anotherKey[0]=1;
        assertThat(new PepperedPasswordEncoder(Base64.getEncoder().encodeToString(anotherKey),10).matches("correct password",hash)).isFalse();
        assertThat(encoder.matches("correct password", new BCryptPasswordEncoder(10).encode("correct password"))).isFalse();
        assertThat(encoder.matches("correct password", PepperedPasswordEncoder.PREFIX + "invalid")).isFalse();
        assertThat(encoder.matches("correct password", null)).isFalse();
        assertThat(encoder.matches(null,hash)).isFalse();
    }
    @Test void missingOrShortPepperFailsClosed() {
        for (String key : new String[]{"", "not-base64", "YWJjZA=="})
            assertThatThrownBy(() -> new PepperedPasswordEncoder(key,12)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void otpDigestBindsChallengeIdAndLeadingZeros() {
        var digester = new OtpDigester(KEY); var id = java.util.UUID.randomUUID();
        String digest = digester.digest(id,"000007");
        assertThat(digester.matches(id,"000007",digest)).isTrue();
        assertThat(digester.matches(java.util.UUID.randomUUID(),"000007",digest)).isFalse();
        assertThat(digester.matches(id,"7",digest)).isFalse();
    }
}
