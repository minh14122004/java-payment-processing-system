package com.example.payment.auth;

import com.example.payment.enums.OtpPurpose;
import com.example.payment.exception.ApiException;
import com.example.payment.service.SmtpOtpMailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpOtpMailSenderTest {
    @Test void formatsPurposeExpiryAndLeadingZerosAndMapsDeliveryFailure() {
        var transport=mock(JavaMailSender.class); var adapter=new SmtpOtpMailSender(transport,"sender@example.test");
        Instant expiry=Instant.parse("2026-10-06T12:05:00Z");
        adapter.send("recipient@example.test",OtpPurpose.PASSWORD_RESET,"000007",expiry);
        var captured=ArgumentCaptor.forClass(SimpleMailMessage.class); verify(transport).send(captured.capture());
        assertThat(captured.getValue().getTo()).containsExactly("recipient@example.test");
        assertThat(captured.getValue().getText()).contains("PASSWORD_RESET","000007",expiry.toString());
        doThrow(new MailSendException("test SMTP error")).when(transport).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> adapter.send("recipient@example.test",OtpPurpose.REGISTRATION,"000007",expiry))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("EMAIL_DELIVERY_FAILED");
    }
}
