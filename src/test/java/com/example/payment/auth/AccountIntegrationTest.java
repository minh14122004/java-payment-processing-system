package com.example.payment.auth;

import com.example.payment.dto.AuthDtos.*;
import com.example.payment.repository.UserRepository;
import com.example.payment.service.OtpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"app.auth.bcrypt-cost=10", "app.auth.cleanup-ms=86400000", "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@Import(AuthTestConfiguration.class)
class AccountIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        var db = PostgresFixture.DATABASE;
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
        registry.add("app.auth.password-pepper", () -> AuthTestConfiguration.PEPPER);
        registry.add("app.auth.otp-key", () -> AuthTestConfiguration.OTP_KEY);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OtpService otp;
    @Autowired UserRepository users;
    @Autowired AuthTestConfiguration.MutableClock clock;
    @Autowired AuthTestConfiguration.RecordingMail mail;
    private static final String PASSWORD = "Account test password";

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE email_otp_challenges, accounts, users, payment_transactions, idempotency_records CASCADE");
        clock.reset(); mail.reset();
    }
    @AfterEach void removeFaults() {
        jdbc.execute("DROP TRIGGER IF EXISTS account_test_failure ON accounts");
    }
    private Registration register(String email) {
        var challenge = otp.register(new Register(email, PASSWORD, "Tên chủ"));
        return otp.confirmRegistration(challenge.challengeId(), mail.lastCode());
    }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    private MvcResult call(MockHttpServletRequestBuilder request, int status) throws Exception {
        var result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return result;
    }
    private final class Client {
        MockHttpSession session;
        String csrf;
        Client() throws Exception { refresh(); }
        void refresh() throws Exception {
            var request = get("/api/auth/csrf");
            if (session != null && !session.isInvalid()) request.session(session);
            var result = call(request, 200);
            session = (MockHttpSession) result.getRequest().getSession();
            csrf = body(result).path("token").asText();
        }
        void login(String email) throws Exception {
            send(post("/api/auth/login"), json.writeValueAsString(Map.of("email", email, "password", PASSWORD)), 200);
            refresh();
        }
        MvcResult send(MockHttpServletRequestBuilder request, String payload, int status) throws Exception {
            if (session != null && !session.isInvalid()) request.session(session);
            return call(request.contentType("application/json").header("X-CSRF-TOKEN", csrf).content(payload), status);
        }
        JsonNode list(String query) throws Exception {
            return body(call(get("/api/accounts" + query).session(session), 200));
        }
        JsonNode create(String name) throws Exception {
            return body(send(post("/api/accounts"), json.writeValueAsString(Map.of("accountName", name)), 201));
        }
    }

    @Test void opensNamedAccountsWithoutNewIdentityOrOtpAndListsOnlyTheirOwner() throws Exception {
        var owner = register("owner-accounts@example.test");
        var other = register("other-accounts@example.test");
        var client = new Client(); client.login(owner.user().email());
        long challenges = jdbc.queryForObject("select count(*) from email_otp_challenges", Long.class);
        int deliveries = mail.deliveries.size();
        var result = client.send(post("/api/accounts"), "{\"accountName\":\"  Tiết kiệm  \"}", 201);
        var created = body(result);
        assertThat(created.path("accountName").asText()).isEqualTo("Tiết kiệm");
        assertThat(created.path("accountHolderName").asText()).isEqualTo("Tên chủ");
        assertThat(created.path("userId").asText()).isEqualTo(owner.user().id().toString());
        assertThat(created.path("balance").decimalValue()).isZero();
        assertThat(created.path("currency").asText()).isEqualTo("VND");
        assertThat(created.path("createdAt").asText()).isNotBlank();
        assertThat(created.path("id").asText()).isNotEqualTo(owner.account().id().toString());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/accounts/" + created.path("id").asText());
        assertThat(body(call(get(result.getResponse().getHeader("Location")).session(client.session), 200))).isEqualTo(created);
        client.create("Tiết kiệm"); // Repeated labels are allowed, including for the same user.
        var duplicateRename = body(client.send(patch("/api/accounts/" + created.path("id").asText()),
                "{\"accountName\":\"Tiết kiệm\"}", 200));
        assertThat(duplicateRename.path("accountName").asText()).isEqualTo("Tiết kiệm");
        var page = client.list("");
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("items")).hasSize(3);
        assertThat(page.toString()).doesNotContain(other.account().id().toString(), "passwordHash", "email", "otpDigest");
        assertThat(users.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from email_otp_challenges", Long.class)).isEqualTo(challenges);
        assertThat(mail.deliveries).hasSize(deliveries);
        assertThat(body(call(get("/api/accounts/" + owner.account().id()).session(client.session), 200))
                .path("accountName").asText()).isEqualTo("tài khoản thanh toán 1");
    }

    @Test void paginationUsesStableDescendingTimestampAndUuidAndSupportsEmptyPages() throws Exception {
        var owner = register("pages@example.test"); var client = new Client(); client.login(owner.user().email());
        client.create("One"); client.create("Two");
        jdbc.update("update accounts set created_at = '2026-10-01T00:00:00Z' where user_id = ?", owner.user().id());
        var expected = jdbc.queryForList("select id::text from accounts where user_id = ? order by created_at desc, id desc", String.class, owner.user().id());
        for (int page = 0; page < 3; page++) {
            var result = client.list("?page=" + page + "&size=1");
            assertThat(result.path("items").get(0).path("id").asText()).isEqualTo(expected.get(page));
            assertThat(result.path("totalElements").asInt()).isEqualTo(3);
            assertThat(result.path("totalPages").asInt()).isEqualTo(3);
        }
        assertThat(client.list("?page=3&size=1").path("items")).isEmpty();
        assertThat(client.list("?page=2147483647&size=100").path("items")).isEmpty();
        // An empty result must not auto-create an account during a read.
        jdbc.update("delete from accounts where user_id = ?", owner.user().id());
        assertThat(client.list("").path("totalElements").asLong()).isZero();
        assertThat(client.list("").path("items")).isEmpty();
    }

    @Test void rejectsInvalidNamesUnknownBodyFieldsAndInvalidPagination() throws Exception {
        var owner = register("validation@example.test"); var client = new Client(); client.login(owner.user().email());
        var invalid = new ArrayList<>(List.of("{}", "{\"accountName\":null}", "{\"accountName\":\"\"}",
                "{\"accountName\":\" \\t\\n\"}", "{\"accountName\":123}", "{\"accountName\":true}",
                "{\"accountName\":[]}", "{\"accountName\":{}}", "{", "null",
                json.writeValueAsString(Map.of("accountName", "a".repeat(101)))));
        for (String field : List.of("userId", "balance", "currency", "accountHolderName", "email", "password", "id"))
            invalid.add(json.writeValueAsString(Map.of("accountName", "Valid", field, "forbidden")));
        for (String payload : invalid) {
            assertThat(body(client.send(post("/api/accounts"), payload, 400)).path("code").asText()).isEqualTo("INVALID_REQUEST");
            assertThat(body(client.send(patch("/api/accounts/" + owner.account().id()), payload, 400)).path("code").asText()).isEqualTo("INVALID_REQUEST");
        }
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=abc", "?size=1.5", "?page=2147483648"))
            assertThat(body(call(get("/api/accounts" + query).session(client.session), 400)).path("code").asText()).isEqualTo("INVALID_REQUEST");
        client.send(patch("/api/accounts/not-a-uuid"), "{\"accountName\":\"Valid\"}", 400);
        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class)).isEqualTo(1);
        assertThat(client.create(" " + "a".repeat(100) + " ").path("accountName").asText()).hasSize(100);
    }

    private void deniedWithoutLogin(Client client, UUID id) throws Exception {
        for (var request : List.of(get("/api/accounts"), get("/api/accounts/" + id), get("/api/accounts/" + id + "/balance"),
                post("/api/accounts"), patch("/api/accounts/" + id))) {
            // No CSRF, and invalid JSON: authentication must be evaluated before both.
            if (client != null && !client.session.isInvalid()) request.session(client.session);
            assertThat(body(call(request.contentType("application/json").content("{"), 401)).path("code").asText())
                    .isEqualTo("AUTHENTICATION_REQUIRED");
        }
    }

    @Test void directRequestsRequireLoginEvenWithAnonymousCsrfAndAfterLogoutOrRevocation() throws Exception {
        var owner = register("guard@example.test");
        deniedWithoutLogin(null, owner.account().id());
        var client = new Client(); deniedWithoutLogin(client, owner.account().id());
        client.send(post("/api/accounts"), "{\"accountName\":\"Blocked\"}", 401);
        client.send(patch("/api/accounts/" + owner.account().id()), "{\"accountName\":\"Blocked\"}", 401);
        client.login(owner.user().email());
        client.send(post("/api/auth/logout"), "{}", 204);
        deniedWithoutLogin(client, owner.account().id());
        client.refresh(); client.login(owner.user().email());
        jdbc.update("update users set credential_version = credential_version + 1 where id = ?", owner.user().id());
        deniedWithoutLogin(client, owner.account().id());
        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select account_name from accounts where id = ?", String.class, owner.account().id()))
                .isEqualTo("tài khoản thanh toán 1");
    }

    @Test void authenticatedMutationsRequireCurrentSessionCsrf() throws Exception {
        var owner = register("csrf-accounts@example.test"); var client = new Client();
        String oldToken = client.csrf; client.login(owner.user().email());
        for (String token : List.of("", "incorrect", oldToken)) {
            for (var request : List.of(post("/api/accounts"), patch("/api/accounts/" + owner.account().id()))) {
                request.session(client.session).contentType("application/json").content("{\"accountName\":\"Blocked\"}");
                if (!token.isEmpty()) request.header("X-CSRF-TOKEN", token);
                assertThat(body(call(request, 403)).path("code").asText()).isEqualTo("CSRF_INVALID");
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select account_name from accounts where id = ?", String.class, owner.account().id()))
                .isEqualTo("tài khoản thanh toán 1");
    }

    @Test void renameRequiresOwnershipAndUpdatesOnlyNameWithoutTouchingMoney() throws Exception {
        var owner = register("rename@example.test"); var other = register("rename-other@example.test");
        var client = new Client(); client.login(owner.user().email());
        String payload = "{\"accountName\":\"  Chi tiêu  \"}";
        assertThat(body(client.send(patch("/api/accounts/" + other.account().id()), payload, 403)).path("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body(client.send(patch("/api/accounts/" + UUID.randomUUID()), payload, 404)).path("code").asText()).isEqualTo("ACCOUNT_NOT_FOUND");
        jdbc.update("update accounts set balance = 123456 where id = ?", owner.account().id());
        var before = jdbc.queryForMap("select id, user_id, account_holder_name, balance, currency, created_at from accounts where id = ?", owner.account().id());
        jdbc.execute("CREATE OR REPLACE FUNCTION account_test_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE TRIGGER account_test_failure BEFORE UPDATE OF balance ON accounts FOR EACH ROW EXECUTE FUNCTION account_test_reject()");
        var renamed = body(client.send(patch("/api/accounts/" + owner.account().id()), payload, 200));
        assertThat(renamed.path("accountName").asText()).isEqualTo("Chi tiêu");
        assertThat(renamed.path("balance").asLong()).isEqualTo(123456);
        assertThat(jdbc.queryForMap("select id, user_id, account_holder_name, balance, currency, created_at from accounts where id = ?", owner.account().id())).isEqualTo(before);
        assertThat(client.list("").path("items").get(0).path("accountName").asText()).isEqualTo("Chi tiêu");
        assertThat(body(call(get("/api/accounts/" + owner.account().id()).session(client.session), 200))).isEqualTo(renamed);
        assertThat(jdbc.queryForObject("select account_name from accounts where id = ?", String.class, other.account().id()))
                .isEqualTo("tài khoản thanh toán 1");
    }

    @Test void failedCreateAndRenameRollBackAndReturnGenericError() throws Exception {
        var owner = register("failure-accounts@example.test"); var client = new Client(); client.login(owner.user().email());
        jdbc.execute("CREATE OR REPLACE FUNCTION account_test_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE TRIGGER account_test_failure BEFORE INSERT OR UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION account_test_reject()");
        for (var request : List.of(post("/api/accounts"), patch("/api/accounts/" + owner.account().id()))) {
            var error = body(client.send(request, "{\"accountName\":\"Fails\"}", 500));
            assertThat(error.path("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(error.toString()).doesNotContain("test failure", "SQL", "Exception");
        }
        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select account_name from accounts where id = ?", String.class, owner.account().id()))
                .isEqualTo("tài khoản thanh toán 1");
    }

    @Test void openApiIncludesNewContractsAndManualCsrfOnPostAndPatch() throws Exception {
        var api = body(call(get("/v3/api-docs"), 200));
        var paths = api.path("paths");
        assertThat(paths.path("/api/accounts").has("get")).isTrue();
        assertThat(paths.path("/api/accounts").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/accounts/{id}").path("patch").path("responses").has("200")).isTrue();
        for (var operation : List.of(paths.path("/api/accounts").path("post"), paths.path("/api/accounts/{id}").path("patch"))) {
            var csrf = new ArrayList<JsonNode>();
            operation.path("parameters").forEach(parameter -> {
                if (parameter.path("name").asText().equals("X-CSRF-TOKEN")) csrf.add(parameter);
            });
            assertThat(csrf).hasSize(1);
            assertThat(csrf.getFirst().path("required").asBoolean()).isTrue();
        }
        assertThat(api.path("components").path("schemas").path("AccountView").path("properties").has("accountName")).isTrue();
        call(get("/swagger-ui/index.html"), 200);
    }
}
