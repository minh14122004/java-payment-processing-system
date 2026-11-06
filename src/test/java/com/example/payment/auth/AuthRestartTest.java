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
    private HttpResponse<String> transfer(String base, String csrf, Object body, String key) throws Exception {
        return keyed(base,"/api/transactions/transfer",csrf,body,key);
    }
    private HttpResponse<String> keyed(String base, String path, String csrf, Object body, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path))
            .header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf).header("Idempotency-Key",key)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void financialHttpWalkthroughAndReplaySurviveApplicationRestart() throws Exception {
        String email="financial-restart-"+UUID.randomUUID()+"@example.test", password="Financial restart password";
        String key=UUID.randomUUID().toString(); Map<String,Object> payload, withdrawalPayload;
        JsonNode original, originalWithdrawal; String source;
        try(var first=start()) {
            String base="http://127.0.0.1:"+first.getWebServer().getPort();
            assertThat(get(base,"/swagger-ui/index.html").statusCode()).isEqualTo(200);
            String csrf=node(get(base,"/api/auth/csrf")).path("token").asText();
            var issued=post(base,"/api/auth/register",csrf,Map.of("email",email,"password",password,"displayName","Tên thật"));
            var verified=post(base,"/api/auth/register/verify",csrf,Map.of("challengeId",node(issued).path("challengeId").asText(),"otp","000007"));
            assertThat(verified.statusCode()).isEqualTo(201);
            source=node(verified).path("account").path("id").asText();
            assertThat(node(verified).path("account").path("accountNumber").asText()).matches("[0-9]{12}");
            assertThat(post(base,"/api/auth/login",csrf,Map.of("email",email,"password",password)).statusCode()).isEqualTo(200);
            csrf=node(get(base,"/api/auth/csrf")).path("token").asText();
            var destination=post(base,"/api/accounts",csrf,Map.of("accountName","Savings"));
            assertThat(destination.statusCode()).isEqualTo(201);
            assertThat(post(base,"/api/transactions/deposit",csrf,Map.of("accountId",source,"amount",1000)).statusCode()).isEqualTo(201);
            payload=Map.of("sourceAccountId",source,"destinationAccountNumber",node(destination).path("accountNumber").asText(),"amount",1000);
            var preview=post(base,"/api/transactions/transfer/preview",csrf,payload);
            assertThat(preview.statusCode()).isEqualTo(200); assertThat(node(preview).path("description").asText()).isEqualTo("Tên thật đã chuyển khoản");
            var committed=transfer(base,csrf,payload,key);
            assertThat(committed.statusCode()).isEqualTo(201); original=node(committed);
            assertThat(node(get(base,committed.headers().firstValue("Location").orElseThrow()))).isEqualTo(original);
            assertThat(node(get(base,"/api/accounts/"+source+"/transactions")).path("totalElements").asInt()).isEqualTo(2);
            String target=node(destination).path("id").asText();
            withdrawalPayload=Map.of("accountId",target,"amount",1000);
            var withdrawn=keyed(base,"/api/transactions/withdraw",csrf,withdrawalPayload,key);
            assertThat(withdrawn.statusCode()).isEqualTo(201); originalWithdrawal=node(withdrawn);
            assertThat(node(get(base,withdrawn.headers().firstValue("Location").orElseThrow()))).isEqualTo(originalWithdrawal);
            assertThat(node(get(base,"/api/accounts/"+target+"/balance")).path("balance").asLong()).isZero();
            assertThat(node(keyed(base,"/api/transactions/withdraw",csrf,withdrawalPayload,key))).isEqualTo(originalWithdrawal);
        }
        try(var second=start()) {
            String base="http://127.0.0.1:"+second.getWebServer().getPort();
            assertThat(transfer(base,"expired",payload,key).statusCode()).isEqualTo(401);
            assertThat(keyed(base,"/api/transactions/withdraw","expired",withdrawalPayload,key).statusCode()).isEqualTo(401);
            String csrf=node(get(base,"/api/auth/csrf")).path("token").asText();
            assertThat(post(base,"/api/auth/login",csrf,Map.of("email",email,"password",password)).statusCode()).isEqualTo(200);
            csrf=node(get(base,"/api/auth/csrf")).path("token").asText();
            var replay=transfer(base,csrf,payload,key);
            assertThat(replay.statusCode()).isEqualTo(200); assertThat(node(replay)).isEqualTo(original);
            assertThat(node(get(base,"/api/accounts/"+source+"/balance")).path("balance").asLong()).isZero();
            assertThat(node(get(base,"/api/accounts/"+source+"/transactions")).path("totalElements").asInt()).isEqualTo(2);
            var replayWithdrawal=keyed(base,"/api/transactions/withdraw",csrf,withdrawalPayload,key);
            assertThat(replayWithdrawal.statusCode()).isEqualTo(200);
            assertThat(node(replayWithdrawal)).isEqualTo(originalWithdrawal);
            String target=withdrawalPayload.get("accountId").toString();
            assertThat(node(get(base,"/api/accounts/"+target+"/balance")).path("balance").asLong()).isZero();
            assertThat(node(get(base,"/api/accounts/"+target+"/transactions")).path("totalElements").asInt()).isEqualTo(2);
            assertThat(post(base,"/api/auth/logout",csrf,Map.of()).statusCode()).isEqualTo(204);
            assertThat(keyed(base,"/api/transactions/withdraw",csrf,withdrawalPayload,key).statusCode()).isEqualTo(401);
        }
    }
    private void assertAccountRoutesRejectExpiredSession(String base, UUID account) throws Exception {
        assertThat(get(base, "/api/accounts").statusCode()).isEqualTo(401);
        assertThat(post(base,"/api/transactions/withdraw","expired",Map.of("accountId",account,"amount",1)).statusCode()).isEqualTo(401);
        for (String method : List.of("POST", "PATCH")) {
            String path = method.equals("POST") ? "/api/accounts" : "/api/accounts/" + account;
            var response = client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString("{\"accountName\":\"Blocked\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(node(response).path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        }
    }
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
            assertAccountRoutesRejectExpiredSession(base, account);
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
            assertAccountRoutesRejectExpiredSession(base, account);
            var jdbc = second.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select account_name from accounts where id = ?", String.class, account))
                    .isEqualTo("tài khoản thanh toán 1");
            assertThat(jdbc.queryForObject("select count(*) from accounts where user_id = (select user_id from accounts where id = ?)", Long.class, account))
                    .isEqualTo(1);
        }
    }
}
