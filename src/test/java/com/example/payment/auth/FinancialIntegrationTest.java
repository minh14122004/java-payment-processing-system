package com.example.payment.auth;

import com.example.payment.dto.TransactionDtos.*;
import com.example.payment.repository.FinancialRepository;
import com.example.payment.security.AuthPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"app.auth.bcrypt-cost=10", "app.auth.cleanup-ms=86400000", "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@Import(AuthTestConfiguration.class)
class FinancialIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        var db = PostgresFixture.DATABASE;
        r.add("spring.datasource.url", db::getJdbcUrl); r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
        r.add("app.auth.password-pepper", () -> AuthTestConfiguration.PEPPER);
        r.add("app.auth.otp-key", () -> AuthTestConfiguration.OTP_KEY);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean FinancialRepository repository;
    private record Party(UUID user, UUID account, String number) {}
    private Party a, b, c, d;

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE email_otp_challenges, accounts, users, payment_transactions, idempotency_records CASCADE");
        a = party("Nguyễn An"); b = party("Người nhận"); c = party("Other"); d = party("Fourth");
    }
    private Party party(String name) {
        UUID user = UUID.randomUUID(), account = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,display_name,password_hash,email_verified_at) VALUES (?,?,?,'fixture',now())", user, user + "@example.test", name);
        jdbc.update("INSERT INTO accounts(id,user_id,account_holder_name,account_name) VALUES (?,?,'Old holder','Private label')", account, user);
        return new Party(user, account, jdbc.queryForObject("SELECT account_number FROM accounts WHERE id=?", String.class, account));
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Party owner) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(new AuthPrincipal(owner.user, 0), null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
    private MvcResult call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expected);
        return result;
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private MockHttpServletRequestBuilder mutation(String route, Party owner, Object payload) throws Exception {
        return as(post(route), owner).with(csrf().asHeader()).contentType("application/json").content(json.writeValueAsString(payload));
    }
    private Map<String,Object> transfer(Party source, Party target, Object amount) {
        return new HashMap<>(Map.of("sourceAccountId", source.account, "destinationAccountNumber", target.number, "amount", amount));
    }
    private MvcResult send(Party source, Party target, Object amount, String key, int status) throws Exception {
        return call(mutation("/api/transactions/transfer", source, transfer(source,target,amount)).header("Idempotency-Key",key),status);
    }
    private void fund(Party party, String value) { jdbc.update("UPDATE accounts SET balance=? WHERE id=?", new BigDecimal(value), party.account); }
    private BigDecimal balance(Party party) { return jdbc.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, party.account); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    @Test void depositReturnsRetrievableImmutableRecordAndEachCallCreditsAgain() throws Exception {
        var request = Map.of("accountId", a.account, "amount", new BigDecimal("1000.0"));
        var result = call(mutation("/api/transactions/deposit",a,request),201);
        var item = body(result);
        assertThat(item.path("amount").toString()).isEqualTo("1000");
        assertThat(item.path("sourceAccountId").isNull()).isTrue();
        assertThat(item.path("sourceAccountNumber").isNull()).isTrue();
        assertThat(item.path("description").isNull()).isTrue();
        assertThat(item.path("destinationAccountNumber").asText()).isEqualTo(a.number);
        assertThat(body(call(as(get(result.getResponse().getHeader("Location")),a),200))).isEqualTo(item);
        call(mutation("/api/transactions/deposit",a,request),201);
        assertThat(balance(a)).isEqualByComparingTo("2000");
        assertThat(count("payment_transactions")).isEqualTo(2);
        assertThat(count("idempotency_records")).isZero();
    }

    @Test void strictMoneyAndBodyValidationRejectsInvalidJsonWithoutWrites() throws Exception {
        for (String value : List.of("null","0","-1","1000.5","10000000000000000000","\"1000\"","true","[]","{}")) {
            String payload = "{\"accountId\":\""+a.account+"\",\"amount\":"+value+"}";
            call(as(post("/api/transactions/deposit"),a).with(csrf().asHeader()).contentType("application/json").content(payload),400);
        }
        for (String payload : List.of("{}","null","{", "{\"accountId\":\"bad\",\"amount\":1}",
                "{\"accountId\":\""+a.account+"\",\"amount\":1,\"balance\":10}"))
            call(as(post("/api/transactions/deposit"),a).with(csrf().asHeader()).contentType("application/json").content(payload),400);
        call(mutation("/api/transactions/deposit",a,Map.of("accountId",UUID.randomUUID(),"amount",1)),404);
        fund(a,"9999999999999999999");
        assertThat(body(call(mutation("/api/transactions/deposit",a,Map.of("accountId",a.account,"amount",1)),422)).path("code").asText())
            .isEqualTo("BALANCE_LIMIT_EXCEEDED");
        assertThat(count("payment_transactions")).isZero();
    }

    @Test void previewUsesUserNamesAndNeverWritesThenCustomMessageIsFrozen() throws Exception {
        fund(a,"1000");
        var payload = transfer(a,b,200);
        var preview = body(call(mutation("/api/transactions/transfer/preview",a,payload),200));
        assertThat(preview.path("description").asText()).isEqualTo("Nguyễn An đã chuyển khoản");
        assertThat(preview.path("recipientName").asText()).isEqualTo("Người nhận");
        assertThat(preview.toString()).doesNotContain("Private label","Old holder","email","balance");
        assertThat(count("payment_transactions")).isZero(); assertThat(count("idempotency_records")).isZero();
        assertThat(balance(a)).isEqualByComparingTo("1000");
        payload.put("description","  Gửi tiền ăn trưa  ");
        var item = body(call(mutation("/api/transactions/transfer",a,payload).header("Idempotency-Key","custom"),201));
        assertThat(item.path("description").asText()).isEqualTo("Gửi tiền ăn trưa");
        assertThat(balance(a)).isEqualByComparingTo("800"); assertThat(balance(b)).isEqualByComparingTo("200");
        call(as(patch("/api/transactions/"+item.path("id").asText()),a).with(csrf().asHeader()).contentType("application/json").content("{}"),405);
    }

    @Test void blankMessagesUseDefaultAndOversizedOrWrongTypedFieldsAreRejected() throws Exception {
        fund(a,"1000");
        for (Object value : Arrays.asList(null,""," \t\n")) {
            var payload=transfer(a,b,1); payload.put("description",value);
            assertThat(body(call(mutation("/api/transactions/transfer/preview",a,payload),200)).path("description").asText())
                .isEqualTo("Nguyễn An đã chuyển khoản");
        }
        for (Object value : List.of("a".repeat(256),123,true,List.of("text"))) {
            var payload=transfer(a,b,1); payload.put("description",value);
            call(mutation("/api/transactions/transfer/preview",a,payload),400);
        }
        for (Object value : List.of("123"," 100000000001","１２３４５６７８９０１２",100000000001L)) {
            var payload=transfer(a,b,1); payload.put("destinationAccountNumber",value);
            call(mutation("/api/transactions/transfer/preview",a,payload),400);
        }
        var unknown=transfer(a,b,1); unknown.put("destinationAccountNumber","999999999999");
        call(mutation("/api/transactions/transfer/preview",a,unknown),404);
        var legacy=transfer(a,b,1); legacy.put("destinationAccountId",b.account);
        call(mutation("/api/transactions/transfer",a,legacy).header("Idempotency-Key","legacy"),400);
    }

    @Test void executionRechecksPreviewAndAllowsDistinctAccountsOfSameOwner() throws Exception {
        fund(a,"1000");
        call(mutation("/api/transactions/transfer/preview",a,transfer(a,b,1000)),200);
        fund(a,"0"); send(a,b,1000,"recheck",422);
        fund(a,"1000");
        assertThat(body(send(a,a,1,"self",422)).path("code").asText()).isEqualTo("SAME_ACCOUNT_TRANSFER");
        fund(b,"9999999999999999999");
        assertThat(body(send(a,b,1,"overflow",422)).path("code").asText()).isEqualTo("BALANCE_LIMIT_EXCEEDED");
        fund(b,"0"); jdbc.update("UPDATE accounts SET user_id=? WHERE id=?",a.user,b.account);
        send(a,b,1000,"recheck",201);
        assertThat(balance(a)).isZero(); assertThat(balance(b)).isEqualByComparingTo("1000");
    }

    @Test void replayNormalizesMoneyAndDefaultModeAndReturnsOriginalAfterNameChange() throws Exception {
        fund(a,"1000"); var original=body(send(a,b,1000,"retry",201));
        jdbc.update("UPDATE users SET display_name='Changed' WHERE id=?",a.user);
        var payload=transfer(a,b,new BigDecimal("1000.0")); payload.put("description","  ");
        assertThat(body(call(mutation("/api/transactions/transfer",a,payload).header("Idempotency-Key","retry"),200))).isEqualTo(original);
        for (String description : List.of("Different","Nguyễn An đã chuyển khoản")) {
            payload.put("description",description);
            assertThat(body(call(mutation("/api/transactions/transfer",a,payload).header("Idempotency-Key","retry"),409)).path("code").asText())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }
        send(a,b,1,"retry",409); send(a,c,1000,"retry",409); send(c,d,1000,"retry",409);
        assertThat(balance(a)).isZero(); assertThat(balance(b)).isEqualByComparingTo("1000");
        assertThat(count("payment_transactions")).isEqualTo(1); assertThat(count("idempotency_records")).isEqualTo(1);
    }

    @Test void keysAreRequiredSingularCaseSensitiveAndNeverTrimmed() throws Exception {
        fund(a,"1000");
        call(mutation("/api/transactions/transfer",a,transfer(a,b,1)),400);
        for (String key : List.of("","has space","\tbad"," leading","trailing ","á","x".repeat(129))) send(a,b,1,key,400);
        call(mutation("/api/transactions/transfer",a,transfer(a,b,1)).header("Idempotency-Key","one","two"),400);
        send(a,b,1,"A",201); send(a,b,1,"a",201); send(a,b,1,"x".repeat(128),201);
        assertThat(count("idempotency_records")).isEqualTo(3);
    }

    @Test void allRoutesEnforceAuthenticationCsrfOwnershipAndCredentialRevocation() throws Exception {
        fund(a,"1000"); var result=body(send(a,b,1,"protected",201));
        for (String route : List.of("/api/transactions/deposit","/api/transactions/transfer","/api/transactions/transfer/preview")) {
            assertThat(body(call(post(route).contentType("application/json").content("{"),401)).path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
            assertThat(body(call(as(post(route),a).contentType("application/json").content("{"),403)).path("code").asText()).isEqualTo("CSRF_INVALID");
        }
        call(mutation("/api/transactions/deposit",b,Map.of("accountId",a.account,"amount",1)),403);
        call(mutation("/api/transactions/transfer",b,transfer(a,b,1)).header("Idempotency-Key","protected"),403);
        call(mutation("/api/transactions/transfer/preview",b,transfer(a,b,1)),403);
        call(as(get("/api/transactions/"+result.path("id").asText()),c),403);
        call(as(get("/api/accounts/"+a.account+"/transactions?page=999"),c),403);
        for (Party party : List.of(a,b)) call(as(get("/api/transactions/"+result.path("id").asText()),party),200);
        jdbc.update("UPDATE users SET credential_version=1 WHERE id=?",a.user);
        send(a,b,1,"protected",401);
        call(as(get("/api/accounts/"+a.account+"/transactions"),a),401);
    }

    @Test void historyIsStablePaginatedParticipantOnlyAndOmitsFailedAttempts() throws Exception {
        fund(a,"1000"); send(a,b,100,"h1",201); send(a,b,200,"h2",201); send(a,b,10000,"failed",422);
        jdbc.execute("UPDATE payment_transactions SET created_at='2026-10-01T00:00:00Z'");
        var expected=jdbc.queryForList("SELECT id::text FROM payment_transactions ORDER BY created_at DESC,id DESC",String.class);
        for (Party party : List.of(a,b)) for (int page=0;page<2;page++) {
            var result=body(call(as(get("/api/accounts/"+party.account+"/transactions?page="+page+"&size=1"),party),200));
            assertThat(result.path("items").get(0).path("id").asText()).isEqualTo(expected.get(page));
            assertThat(result.path("totalElements").asInt()).isEqualTo(2); assertThat(result.path("totalPages").asInt()).isEqualTo(2);
        }
        for (String query : List.of("?page=2&size=1","?page=2147483647&size=100"))
            assertThat(body(call(as(get("/api/accounts/"+a.account+"/transactions"+query),a),200)).path("items")).isEmpty();
        assertThat(body(call(as(get("/api/accounts/"+c.account+"/transactions"),c),200)).path("items")).isEmpty();
        for (String query : List.of("?page=-1","?size=0","?size=101","?page=x","?size=1.5"))
            call(as(get("/api/accounts/"+a.account+"/transactions"+query),a),400);
        call(as(get("/api/accounts/"+UUID.randomUUID()+"/transactions"),a),404);
        call(as(get("/api/transactions/"+UUID.randomUUID()),a),404);
    }

    @Test void failuresAfterDebitAndDuringRecordOrKeyWriteRollBackEverythingAndAllowRetry() throws Exception {
        fund(a,"1000");
        doThrow(new IllegalStateException("injected after debit")).when(repository).balance(eq(b.account),any());
        send(a,b,100,"fault",500);
        assertUnchanged(); reset(repository);
        doThrow(new IllegalStateException("injected transaction insert")).when(repository).insert(anyString(),any(),any(),any(),any());
        send(a,b,100,"fault",500);
        call(mutation("/api/transactions/deposit",a,Map.of("accountId",a.account,"amount",100)),500);
        assertUnchanged(); reset(repository);
        doThrow(new IllegalStateException("injected key insert")).when(repository).saveKey(anyString(),anyString(),any());
        send(a,b,100,"fault",500); assertUnchanged(); reset(repository);
        send(a,b,100,"fault",201);
    }
    private void assertUnchanged() {
        assertThat(balance(a)).isEqualByComparingTo("1000"); assertThat(balance(b)).isZero();
        assertThat(count("payment_transactions")).isZero(); assertThat(count("idempotency_records")).isZero();
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one=executor.submit(() -> { start.await(10,TimeUnit.SECONDS); return first.call(); });
            var two=executor.submit(() -> { start.await(10,TimeUnit.SECONDS); return second.call(); });
            return List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        }
    }
    private int transferStatus(Party source, Party target, int amount, String key) throws Exception {
        return mvc.perform(mutation("/api/transactions/transfer",source,transfer(source,target,amount)).header("Idempotency-Key",key)).andReturn().getResponse().getStatus();
    }
    @Test void concurrentDepositsAndCompetingTransfersDoNotLoseMoneyOrOverdraw() throws Exception {
        var deposits=concurrently(
            () -> mvc.perform(mutation("/api/transactions/deposit",a,Map.of("accountId",a.account,"amount",100))).andReturn().getResponse().getStatus(),
            () -> mvc.perform(mutation("/api/transactions/deposit",a,Map.of("accountId",a.account,"amount",200))).andReturn().getResponse().getStatus());
        assertThat(deposits).containsExactly(201,201); assertThat(balance(a)).isEqualByComparingTo("300");
        var transfers=concurrently(() -> transferStatus(a,b,200,"compete1"),() -> transferStatus(a,c,200,"compete2"));
        assertThat(transfers).containsExactlyInAnyOrder(201,422); assertThat(balance(a)).isEqualByComparingTo("100");
        assertThat(balance(a).add(balance(b)).add(balance(c))).isEqualByComparingTo("300");
        assertThat(count("payment_transactions")).isEqualTo(3);
    }
    @Test void oppositeDirectionsAndSameKeyConcurrentCallsConserveBalances() throws Exception {
        fund(a,"1000"); fund(b,"1000");
        assertThat(concurrently(() -> transferStatus(a,b,100,"ab"),() -> transferStatus(b,a,200,"ba"))).containsExactly(201,201);
        assertThat(balance(a)).isEqualByComparingTo("1100"); assertThat(balance(b)).isEqualByComparingTo("900");
        assertThat(concurrently(() -> transferStatus(a,b,1100,"same"),() -> transferStatus(a,b,1100,"same"))).containsExactlyInAnyOrder(201,200);
        assertThat(balance(a)).isZero(); assertThat(balance(b)).isEqualByComparingTo("2000");
        assertThat(count("idempotency_records")).isEqualTo(3);
    }
    @Test void sameKeyOnDisjointPairsRollsBackTheLosingFinancialWrites() throws Exception {
        fund(a,"1000"); fund(c,"1000");
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> { barrier.await(10,TimeUnit.SECONDS); return invocation.callRealMethod(); })
            .when(repository).saveKey(eq("global"),anyString(),any());
        assertThat(concurrently(() -> transferStatus(a,b,100,"global"),() -> transferStatus(c,d,200,"global")))
            .containsExactlyInAnyOrder(201,409);
        assertThat(count("payment_transactions")).isEqualTo(1); assertThat(count("idempotency_records")).isEqualTo(1);
        assertThat(balance(a).add(balance(b))).isEqualByComparingTo("1000");
        assertThat(balance(c).add(balance(d))).isEqualByComparingTo("1000");
        assertThat(balance(b).signum()+balance(d).signum()).isEqualTo(1);
    }
    @Test void heldLockTimesOutAndRollsBackWithRetryableConflict() throws Exception {
        fund(a,"1000"); var db=PostgresFixture.DATABASE;
        try (var connection=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword())) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT id FROM accounts WHERE id=? FOR UPDATE")) {
                lock.setObject(1,a.account); lock.executeQuery().close();
                long start=System.nanoTime();
                assertThat(body(send(a,b,100,"timeout",409)).path("code").asText()).isEqualTo("CONCURRENCY_CONFLICT");
                assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)).isBetween(4L,10L);
            } finally { connection.rollback(); }
        }
        assertUnchanged(); send(a,b,100,"timeout",201);
    }
    @Test void balanceUpdatesPreserveConcurrentRenameAndNumbers() throws Exception {
        assertThat(concurrently(
            () -> mvc.perform(mutation("/api/transactions/deposit",a,Map.of("accountId",a.account,"amount",100))).andReturn().getResponse().getStatus(),
            () -> call(as(patch("/api/accounts/"+a.account),a).with(csrf().asHeader()).contentType("application/json").content("{\"accountName\":\"Renamed\"}"),200).getResponse().getStatus()))
            .containsExactly(201,200);
        assertThat(balance(a)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT account_name FROM accounts WHERE id=?",String.class,a.account)).isEqualTo("Renamed");
        assertThat(jdbc.queryForObject("SELECT account_number FROM accounts WHERE id=?",String.class,a.account)).isEqualTo(a.number);
    }
    @Test void openApiAdvertisesAllRoutesKeyAndManualCsrf() throws Exception {
        var api=body(call(get("/v3/api-docs"),200));
        for (String route : List.of("/api/transactions/deposit","/api/transactions/transfer","/api/transactions/transfer/preview")) {
            var parameters=api.path("paths").path(route).path("post").path("parameters");
            assertThat(parameters.toString()).contains("X-CSRF-TOKEN");
            if(route.endsWith("/transfer")) assertThat(parameters.toString()).contains("Idempotency-Key");
        }
        assertThat(api.path("components").path("schemas").path("AccountView").path("properties").has("accountNumber")).isTrue();
        call(get("/swagger-ui/index.html"),200);
    }

    @Test void concurrentAccountCreationGeneratesDistinctNumbersAndClientCannotChooseThem() throws Exception {
        assertThat(concurrently(
            () -> call(mutation("/api/accounts",a,Map.of("accountName","One")),201).getResponse().getStatus(),
            () -> call(mutation("/api/accounts",a,Map.of("accountName","Two")),201).getResponse().getStatus())).containsExactly(201,201);
        var numbers=jdbc.queryForList("SELECT account_number FROM accounts WHERE user_id=?",String.class,a.user);
        assertThat(numbers).hasSize(3).doesNotHaveDuplicates().allMatch(number -> number.matches("[0-9]{12}"));
        call(mutation("/api/accounts",a,Map.of("accountName","Chosen","accountNumber","123456789012")),400);
        call(as(patch("/api/accounts/"+a.account),a).with(csrf().asHeader()).contentType("application/json")
            .content("{\"accountName\":\"Changed\",\"accountNumber\":\"123456789012\"}"),400);
    }
}
