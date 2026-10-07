package com.example.payment.security;

import com.example.payment.config.AuthConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AuthConfigurationTest {
    @Test void missingPepperPreventsStartupAndEqualKeysAreRejected() {
        var runner=new ApplicationContextRunner().withUserConfiguration(AuthConfiguration.class);
        runner.withPropertyValues("app.auth.password-pepper=", "app.auth.otp-key=AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.auth.password-pepper=AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "app.auth.otp-key=AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .run(context -> assertThat(context).hasFailed());
    }
}
