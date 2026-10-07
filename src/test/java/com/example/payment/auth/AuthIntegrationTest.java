package com.example.payment.auth;

import com.example.payment.dto.AuthDtos.*;
import com.example.payment.enums.OtpPurpose;
import com.example.payment.exception.ApiException;
import com.example.payment.repository.*;
import com.example.payment.security.*;
import com.example.payment.service.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"app.auth.bcrypt-cost=10", "app.auth.cleanup-ms=86400000", "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@Import(AuthTestConfiguration.class)
class AuthIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        var db=PostgresFixture.DATABASE;
        registry.add("spring.datasource.url",db::getJdbcUrl); registry.add("spring.datasource.username",db::getUsername);
        registry.add("spring.datasource.password",db::getPassword);
        registry.add("app.auth.password-pepper",() -> AuthTestConfiguration.PEPPER);
        registry.add("app.auth.otp-key",() -> AuthTestConfiguration.OTP_KEY);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired OtpService otp;
    @Autowired UserRepository users;
    @Autowired EmailOtpRepository challenges;
    @Autowired AuthTestConfiguration.MutableClock clock;
    @Autowired AuthTestConfiguration.RecordingMail mail;
    private static final String PASSWORD=" mật khẩu 😀 ".repeat(8);
    @BeforeEach void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_account ON accounts");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_otp ON email_otp_challenges");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_password ON users");
        jdbc.execute("TRUNCATE email_otp_challenges, accounts, users, payment_transactions, idempotency_records CASCADE");
        clock.reset(); mail.reset();
    }
    private final class Client {
        MockHttpSession session;
        String csrf;
        Client() throws Exception { refresh(); }
        void refresh() throws Exception {
            var request=get("/api/auth/csrf"); if(session!=null && !session.isInvalid()) request.session(session);
            var result=mvc.perform(request).andReturn(); assertThat(result.getResponse().getStatus()).isEqualTo(200);
            session=(MockHttpSession)result.getRequest().getSession(); csrf=read(result).get("token").asText();
        }
        MvcResult postTo(String path, Object body, int expected) throws Exception {
            var request=post("/api/auth/"+path).contentType("application/json").header("X-CSRF-TOKEN",csrf).content(json.writeValueAsBytes(body));
            if(session!=null && !session.isInvalid()) request.session(session);
            var result=mvc.perform(request).andReturn();
            assertThat(result.getResponse().getStatus()).as(path+": "+result.getResponse().getContentAsString()).isEqualTo(expected);
            return result;
        }
        JsonNode login(String email,String password,int status) throws Exception {
            JsonNode result=read(postTo("login",Map.of("email",email,"password",password),status));
            if(status==200) refresh(); return result;
        }
        MvcResult account(UUID id,int status) throws Exception {
            var request=get("/api/accounts/"+id); if(session!=null && !session.isInvalid()) request.session(session);
            var result=mvc.perform(request).andReturn(); assertThat(result.getResponse().getStatus()).isEqualTo(status); return result;
        }
    }
    JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    Registration register(String email) {
        var challenge=otp.register(new Register(email,PASSWORD,"Demo"));
        return otp.confirmRegistration(challenge.challengeId(),mail.lastCode());
    }
    @Test void registrationHashRoundTripAndSessionActuallyAuthenticateNextRequest() throws Exception {
        var client=new Client();
        var issued=read(client.postTo("register",Map.of("email","  DEMO@example.test ","password",PASSWORD,"displayName"," Demo "),202));
        UUID challenge=UUID.fromString(issued.get("challengeId").asText());
        String pending=challenges.findByChallengeId(challenge).orElseThrow().getPendingPasswordHash();
        assertThat(pending).startsWith(PepperedPasswordEncoder.PREFIX).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD,pending)).isTrue(); assertThat(users.count()).isZero();
        client.login("demo@example.test",PASSWORD,401);
        var verified=read(client.postTo("register/verify",Map.of("challengeId",challenge,"otp",mail.lastCode()),201));
        UUID account=UUID.fromString(verified.get("account").get("id").asText());
        assertThat(users.findByEmail("demo@example.test").orElseThrow().getPasswordHash()).isEqualTo(pending);
        assertThat(challenges.findByChallengeId(challenge).orElseThrow().getPendingPasswordHash()).isNull();
        client.account(account,401);
        String oldSession=client.session.getId();
        client.login("  DEMO@EXAMPLE.TEST ",PASSWORD,200);
        assertThat(client.session.getId()).isNotEqualTo(oldSession);
        client.account(account,200);
        client.postTo("logout",Map.of(),204); client.account(account,401);
        var wrong=new Client(); wrong.login("demo@example.test",PASSWORD+"x",401);
        assertThat(verified.toString()).doesNotContain("passwordHash","otpDigest",PASSWORD);
    }
    @Test void changeAndResetInvalidateOldPasswordsAllSessionsAndOutstandingChallenges() throws Exception {
        var registered=register("change@example.test"); var a=new Client(); var b=new Client();
        a.login(registered.user().email(),PASSWORD,200); b.login(registered.user().email(),PASSWORD,200);
        var reset=otp.requestReset(registered.user().email());
        a.postTo("password-change/request",Map.of("currentPassword","wrong password"),401);
        var change=read(a.postTo("password-change/request",Map.of("currentPassword",PASSWORD),202));
        a.postTo("password-change/confirm",Map.of("challengeId",change.get("challengeId").asText(),"otp",mail.lastCode(),"newPassword","new password 😀"),204);
        b.account(registered.account().id(),401);
        var c=new Client(); c.login(registered.user().email(),PASSWORD,401); c.login(registered.user().email(),"new password 😀",200);
        c.postTo("password-reset/confirm",Map.of("challengeId",reset.challengeId(),"otp","000007","newPassword","another password"),400);
        clock.advance(60);
        var anonymous=new Client(); var recovery=read(anonymous.postTo("password-reset/request",Map.of("email",registered.user().email()),202));
        anonymous.postTo("password-reset/confirm",Map.of("challengeId",recovery.get("challengeId").asText(),"otp",mail.lastCode(),"newPassword","recovered password"),204);
        c.account(registered.account().id(),401);
        var d=new Client(); d.login(registered.user().email(),"new password 😀",401); d.login(registered.user().email(),"recovered password",200);
        assertThat(users.count()).isEqualTo(1); assertThat(jdbc.queryForObject("select count(*) from accounts",Long.class)).isEqualTo(1);
        assertThat(users.findByEmail(registered.user().email()).orElseThrow().getCredentialVersion()).isEqualTo(2);
    }
    @Test void errorsCsrfOwnershipAndStrictJson() throws Exception {
        var first=register("owner@example.test"); var second=register("other@example.test"); var client=new Client();
        assertThat(read(client.postTo("password-reset/request",Map.of("email","absent@example.test"),404)).get("code").asText()).isEqualTo("EMAIL_NOT_FOUND");
        client.postTo("register",Map.of("email","fresh@example.test","password",PASSWORD,"displayName","Demo","username","x"),400);
        client.postTo("register",Map.of("email","fresh@example.test","password","😀".repeat(129),"displayName","Demo"),400);
        client.postTo("register",Map.of("email","fresh@example.test","password",123456789,"displayName","Demo"),400);
        client.postTo("register",Map.of("email"," OWNER@EXAMPLE.TEST ","password",PASSWORD,"displayName","Demo"),409);
        var missing=mvc.perform(post("/api/auth/login").contentType("application/json").content("{}" )).andReturn();
        assertThat(read(missing).get("code").asText()).isEqualTo("CSRF_INVALID");
        var protectedMissing=mvc.perform(post("/api/auth/password-change/request").contentType("application/json").content("{}" )).andReturn();
        assertThat(protectedMissing.getResponse().getStatus()).isEqualTo(401);
        client.login(first.user().email(),PASSWORD,200); client.account(second.account().id(),403); client.account(UUID.randomUUID(),404);
        assertThat(mvc.perform(get("/api/accounts/"+first.account().id()+"/balance").session(client.session)).andReturn().getResponse().getStatus()).isEqualTo(200);
        for(String url:List.of("/v3/api-docs","/swagger-ui/index.html"))
            assertThat(mvc.perform(get(url)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
    @Test void credentialLimitSharedWithCurrentPasswordAndAppliesToUnknownEmails() throws Exception {
        var user=register("limited@example.test"); var client=new Client(); client.login(user.user().email(),PASSWORD,200);
        for(int i=0;i<3;i++) client.login(user.user().email(),"wrong password",401);
        for(int i=0;i<2;i++) client.postTo("password-change/request",Map.of("currentPassword","wrong password"),401);
        assertThat(client.postTo("password-change/request",Map.of("currentPassword",PASSWORD),429).getResponse().getHeader("Retry-After")).isNotBlank();
        for(int i=0;i<5;i++) client.login("unknown-limit@example.test",PASSWORD,401);
        client.login("unknown-limit@example.test",PASSWORD,429);
        clock.advance(900); client.login(user.user().email(),PASSWORD,200);
    }
    @Test void rejectsScalarCoercionAcrossAllSecretFieldsWithoutSendingMail() throws Exception {
        var registered=register("json@example.test"); var client=new Client();
        client.login(registered.user().email(),PASSWORD,200);
        int sent=mail.deliveries.size();
        for(Object invalid:List.of(123456789,123456789.25,true)) {
            client.postTo("register",Map.of("email","scalar@example.test","password",invalid,"displayName","Demo"),400);
            client.postTo("login",Map.of("email",registered.user().email(),"password",invalid),400);
            client.postTo("password-change/request",Map.of("currentPassword",invalid),400);
            client.postTo("password-reset/confirm",Map.of("challengeId",UUID.randomUUID(),"otp","000007","newPassword",invalid),400);
            client.postTo("register/verify",Map.of("challengeId",UUID.randomUUID(),"otp",invalid),400);
        }
        client.postTo("login",Map.of("username","Demo","password",PASSWORD),400);
        client.postTo("login",Map.of("email","Demo","password",PASSWORD),400);
        assertThat(mail.deliveries).hasSize(sent);
    }
    @Test void anotherUserCannotConfirmOrResendPasswordChange() throws Exception {
        var owner=register("otp-owner@example.test"); var stranger=register("otp-stranger@example.test");
        var principal=new AuthPrincipal(owner.user().id(),0);
        var change=otp.requestChange(principal,PASSWORD); var other=new Client(); var anonymous=new Client();
        other.login(stranger.user().email(),PASSWORD,200); clock.advance(60);
        other.postTo("password-change/confirm",Map.of("challengeId",change.challengeId(),"otp","000007","newPassword","other password"),400);
        other.postTo("otp/resend",Map.of("challengeId",change.challengeId()),400);
        anonymous.postTo("otp/resend",Map.of("challengeId",change.challengeId()),401);
        assertThat(challenges.findByChallengeId(change.challengeId()).orElseThrow().getFailedAttempts()).isZero();
        var next=otp.resend(change.challengeId(),principal);
        assertThatThrownBy(() -> otp.confirmPassword(OtpPurpose.PASSWORD_CHANGE,principal,new PasswordConfirm(change.challengeId(),"000007","new password")))
                .extracting("code").isEqualTo("OTP_INVALID");
        otp.confirmPassword(OtpPurpose.PASSWORD_CHANGE,principal,new PasswordConfirm(next.challengeId(),"000007","new password"));
    }
    @Test void concurrentWrongCodesCannotExceedAttemptLimitAndIssuanceIsSerialized() throws Exception {
        var pending=otp.register(new Register("attempt-race@example.test",PASSWORD,"Demo"));
        for(int i=0;i<4;i++) assertThatThrownBy(() -> otp.confirmRegistration(pending.challengeId(),"111111")).isInstanceOf(ApiException.class);
        var attempts=race(() -> otp.confirmRegistration(pending.challengeId(),"111111"), () -> otp.confirmRegistration(pending.challengeId(),"111111"));
        assertThat(attempts).allMatch(ApiException.class::isInstance);
        assertThat(challenges.findByChallengeId(pending.challengeId()).orElseThrow().getFailedAttempts()).isEqualTo(5);
        var issued=race(() -> otp.register(new Register("issue-race@example.test",PASSWORD,"First")),
                () -> otp.register(new Register(" ISSUE-RACE@EXAMPLE.TEST ",PASSWORD,"Second")));
        assertThat(issued.stream().filter(Challenge.class::isInstance).count()).isEqualTo(1);
        assertThat(issued.stream().filter(ApiException.class::isInstance).map(ApiException.class::cast)).allMatch(error -> error.code().equals("RATE_LIMITED"));
        assertThat(mail.deliveries.stream().filter(delivery -> delivery.email().equals("issue-race@example.test")).count()).isEqualTo(1);
    }
    @Test void httpDatabaseAndMailFailuresReturnSafeErrorsAndPreserveState() throws Exception {
        var client=new Client(); mail.fail=true;
        var failed=client.postTo("register",Map.of("email","http-mail@example.test","password",PASSWORD,"displayName","Demo"),503);
        assertThat(read(failed).get("code").asText()).isEqualTo("EMAIL_DELIVERY_FAILED");
        mail.fail=false;
        var pending=otp.register(new Register("http-db@example.test",PASSWORD,"Demo"));
        jdbc.execute("CREATE OR REPLACE FUNCTION test_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'private database failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_account BEFORE INSERT ON accounts FOR EACH ROW EXECUTE FUNCTION test_reject()");
        var error=client.postTo("register/verify",Map.of("challengeId",pending.challengeId(),"otp","000007"),500);
        assertThat(read(error).get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.getResponse().getContentAsString()).doesNotContain("private database failure","INSERT","Exception",PASSWORD,"000007");
        assertThat(users.count()).isZero();
        assertThat(challenges.findByChallengeId(pending.challengeId()).orElseThrow().getConsumedAt()).isNull();
    }
    @Test void attemptsExpiryReplayPurposeAndResendPersistCorrectly() {
        var first=otp.register(new Register("otp@example.test",PASSWORD,"Demo"));
        for(int i=0;i<5;i++) assertThatThrownBy(() -> otp.confirmRegistration(first.challengeId(),"111111")).isInstanceOf(ApiException.class);
        assertThat(challenges.findByChallengeId(first.challengeId()).orElseThrow().getFailedAttempts()).isEqualTo(5);
        assertThatThrownBy(() -> otp.confirmRegistration(first.challengeId(),"000007")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> otp.resend(first.challengeId(),null)).extracting("code").isEqualTo("RATE_LIMITED");
        clock.advance(60); var next=otp.resend(first.challengeId(),null);
        assertThat(next.challengeId()).isNotEqualTo(first.challengeId());
        assertThatThrownBy(() -> otp.confirmRegistration(first.challengeId(),"000007")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> otp.confirmPassword(OtpPurpose.PASSWORD_RESET,null,new PasswordConfirm(next.challengeId(),"000007","new password"))).isInstanceOf(ApiException.class);
        clock.advance(300); assertThatThrownBy(() -> otp.confirmRegistration(next.challengeId(),"000007")).isInstanceOf(ApiException.class);
        var renewed=otp.register(new Register("otp@example.test","new password","New Demo"));
        var confirmed=otp.confirmRegistration(renewed.challengeId(),"000007");
        assertThat(confirmed.user().displayName()).isEqualTo("New Demo");
        assertThat(encoder.matches("new password",users.findById(confirmed.user().id()).orElseThrow().getPasswordHash())).isTrue();
        assertThatThrownBy(() -> otp.confirmRegistration(renewed.challengeId(),"000007")).isInstanceOf(ApiException.class);
    }
    @Test void smtpFailuresConsumeQuotaAndPreservePreviousCode() {
        var first=otp.register(new Register("mail@example.test",PASSWORD,"Demo"));
        clock.advance(60); mail.fail=true;
        assertThatThrownBy(() -> otp.resend(first.challengeId(),null)).extracting("code").isEqualTo("EMAIL_DELIVERY_FAILED");
        assertThat(challenges.findByChallengeId(first.challengeId()).orElseThrow().getWindowSendCount()).isEqualTo(2);
        assertThatThrownBy(() -> otp.resend(first.challengeId(),null)).extracting("code").isEqualTo("RATE_LIMITED");
        assertThat(otp.confirmRegistration(first.challengeId(),"000007").user().email()).isEqualTo("mail@example.test");
        for(int i=0;i<5;i++) {
            assertThatThrownBy(() -> otp.register(new Register("fail@example.test",PASSWORD,"Demo"))).extracting("code").isEqualTo("EMAIL_DELIVERY_FAILED");
            clock.advance(60);
        }
        assertThatThrownBy(() -> otp.register(new Register("fail@example.test",PASSWORD,"Demo"))).extracting("code").isEqualTo("RATE_LIMITED");
        clock.advance(3600); mail.fail=false;
        assertThat(otp.register(new Register("fail@example.test",PASSWORD,"Demo"))).isNotNull();
    }
    @Test void databaseFailuresRollbackUserAccountPasswordAndOtpTogether() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        var pending=otp.register(new Register("rollback@example.test",PASSWORD,"Demo"));
        jdbc.execute("CREATE TRIGGER fail_account BEFORE INSERT ON accounts FOR EACH ROW EXECUTE FUNCTION test_reject()");
        assertThatThrownBy(() -> otp.confirmRegistration(pending.challengeId(),"000007")).isInstanceOf(RuntimeException.class);
        assertThat(users.count()).isZero(); assertThat(challenges.findByChallengeId(pending.challengeId()).orElseThrow().getConsumedAt()).isNull();
        jdbc.execute("DROP TRIGGER fail_account ON accounts");
        var user=otp.confirmRegistration(pending.challengeId(),"000007"); var reset=otp.requestReset(user.user().email());
        jdbc.execute("CREATE CONSTRAINT TRIGGER fail_password AFTER UPDATE ON users DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_reject()");
        assertThatThrownBy(() -> otp.confirmPassword(OtpPurpose.PASSWORD_RESET,null,new PasswordConfirm(reset.challengeId(),"000007","new password"))).isInstanceOf(RuntimeException.class);
        var reloaded=users.findById(user.user().id()).orElseThrow();
        assertThat(reloaded.getCredentialVersion()).isZero(); assertThat(encoder.matches(PASSWORD,reloaded.getPasswordHash())).isTrue();
        assertThat(challenges.findByChallengeId(reset.challengeId()).orElseThrow().getConsumedAt()).isNull();
    }
    @Test void acceptedEmailButFailedCommitKeepsOldChallengeAndReservedQuota() {
        var first=otp.register(new Register("commit@example.test",PASSWORD,"Demo")); clock.advance(60);
        jdbc.execute("CREATE OR REPLACE FUNCTION test_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER fail_otp AFTER UPDATE ON email_otp_challenges DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (OLD.challenge_id IS DISTINCT FROM NEW.challenge_id) EXECUTE FUNCTION test_reject()");
        assertThatThrownBy(() -> otp.resend(first.challengeId(),null)).isInstanceOf(RuntimeException.class);
        assertThat(mail.deliveries).hasSize(2);
        var old=challenges.findByChallengeId(first.challengeId()).orElseThrow(); assertThat(old.getWindowSendCount()).isEqualTo(2);
        assertThat(otp.confirmRegistration(first.challengeId(),"000007")).isNotNull();
    }
    @Test void simultaneousConfirmationsCreateExactlyOnePair() throws Exception {
        var challenge=otp.register(new Register("race@example.test",PASSWORD,"Demo"));
        var results=race(() -> otp.confirmRegistration(challenge.challengeId(),"000007"), () -> otp.confirmRegistration(challenge.challengeId(),"000007"));
        assertThat(results.stream().filter(Registration.class::isInstance).count()).isEqualTo(1);
        assertThat(users.count()).isEqualTo(1); assertThat(jdbc.queryForObject("select count(*) from accounts",Long.class)).isEqualTo(1);
    }
    @Test void resendCompetingWithVerifyAndChangeCompetingWithResetRemainAtomic() throws Exception {
        var pending=otp.register(new Register("resendrace@example.test",PASSWORD,"Demo")); clock.advance(60);
        var results=race(() -> otp.resend(pending.challengeId(),null), () -> otp.confirmRegistration(pending.challengeId(),"000007"));
        assertThat(results.stream().filter(value -> !(value instanceof Exception)).count()).isEqualTo(1);
        if(users.count()==0) {
            var active=(Challenge)results.stream().filter(Challenge.class::isInstance).findFirst().orElseThrow(); otp.confirmRegistration(active.challengeId(),"000007");
        }
        var user=users.findByEmail("resendrace@example.test").orElseThrow(); var principal=new AuthPrincipal(user.getId(),user.getCredentialVersion());
        var change=otp.requestChange(principal,PASSWORD); var reset=otp.requestReset(user.getEmail());
        var changed=race(() -> { otp.confirmPassword(OtpPurpose.PASSWORD_CHANGE,principal,new PasswordConfirm(change.challengeId(),"000007","changed password")); return true; },
                () -> { otp.confirmPassword(OtpPurpose.PASSWORD_RESET,null,new PasswordConfirm(reset.challengeId(),"000007","reset password")); return true; });
        assertThat(changed.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1);
        assertThat(users.findById(user.getId()).orElseThrow().getCredentialVersion()).isEqualTo(1);
    }
    @Test void cleanupClearsExpiredSecretsWithoutErasingActiveRateWindows() {
        var pending=otp.register(new Register("cleanup@example.test",PASSWORD,"Demo")); clock.advance(300); otp.cleanup();
        var slot=challenges.findByChallengeId(pending.challengeId()).orElseThrow();
        assertThat(slot.getOtpDigest()).isNull(); assertThat(slot.getPendingPasswordHash()).isNull(); assertThat(slot.getWindowSendCount()).isEqualTo(1);
        clock.advance(3300); otp.cleanup(); assertThat(challenges.findByChallengeId(pending.challengeId())).isEmpty();
    }
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures=new ArrayList<>();
            for(var task:List.of(first,second)) futures.add(pool.submit(() -> { barrier.await(10,TimeUnit.SECONDS); try { return task.call(); } catch(Exception ex) { return ex; } }));
            return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));
        }
    }
}
