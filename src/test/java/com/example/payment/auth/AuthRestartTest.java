package com.example.payment.auth;

import com.example.payment.PaymentProcessingApplication;
import com.example.payment.dto.AuthDtos.Register;
import com.example.payment.service.OtpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AuthRestartTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
    private ServletWebServerApplicationContext start() {
        var db=PostgresFixture.DATABASE;
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(PaymentProcessingApplication.class, AuthTestConfiguration.class).run(
                "--server.port=0", "--server.servlet.session.cookie.secure=false", "--spring.datasource.url="+db.getJdbcUrl(),
                "--spring.datasource.username="+db.getUsername(),"--spring.datasource.password="+db.getPassword(),
                "--app.auth.password-pepper="+AuthTestConfiguration.PEPPER,"--app.auth.otp-key="+AuthTestConfiguration.OTP_KEY,
                "--app.auth.bcrypt-cost=10","--app.auth.cleanup-ms=86400000");
    }
    private HttpResponse<String> get(String base,String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String base,String path,String csrf,Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json")
                .header("X-CSRF-TOKEN",csrf).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode node(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    @Test void persistedHashesAndOtpQuotasSurviveRestartWhileSessionsDoNot() throws Exception {
        String email="restart-"+UUID.randomUUID()+"@example.test";
        String password="😀".repeat(128); UUID account;
        try(var first=start()) {
            String base="http://127.0.0.1:"+first.getWebServer().getPort();
            String csrf=node(get(base,"/api/auth/csrf")).get("token").asText();
            var issued=post(base,"/api/auth/register",csrf,Map.of("email",email,"password",password,"displayName","Restart"));
            assertThat(issued.statusCode()).isEqualTo(202);
            var verified=post(base,"/api/auth/register/verify",csrf,Map.of("challengeId",node(issued).get("challengeId").asText(),"otp","000007"));
            assertThat(verified.statusCode()).isEqualTo(201); account=UUID.fromString(node(verified).get("account").get("id").asText());
            var login=post(base,"/api/auth/login",csrf,Map.of("email",email,"password",password));
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.headers().allValues("set-cookie").toString()).contains("HttpOnly","SameSite=Lax");
            assertThat(get(base,"/api/accounts/"+account).statusCode()).isEqualTo(200);
            first.getBean(OtpService.class).register(new Register("pending-"+email,password,"Pending"));
        }
        try(var second=start()) {
            String base="http://127.0.0.1:"+second.getWebServer().getPort();
            assertThat(get(base,"/api/accounts/"+account).statusCode()).isEqualTo(401);
            String csrf=node(get(base,"/api/auth/csrf")).get("token").asText();
            var limited=post(base,"/api/auth/register",csrf,Map.of("email","pending-"+email,"password",password,"displayName","Pending"));
            assertThat(limited.statusCode()).isEqualTo(429);
            assertThat(post(base,"/api/auth/login",csrf,Map.of("email",email,"password",password)).statusCode()).isEqualTo(200);
            assertThat(get(base,"/api/accounts/"+account).statusCode()).isEqualTo(200);
            var tomcat=(org.springframework.boot.web.embedded.tomcat.TomcatWebServer)second.getWebServer();
            var servletContext=(org.apache.catalina.Context)tomcat.getTomcat().getHost().findChildren()[0];
            String sessionId=cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
            var session=servletContext.getManager().findSession(sessionId);
            assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
            session.setMaxInactiveInterval(1); // Shorten only this test session; exercise the real servlet timeout.
            Thread.sleep(1500);
            assertThat(get(base,"/api/accounts/"+account).statusCode()).isEqualTo(401);
        }
    }
}
