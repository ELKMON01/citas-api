package co.com.fcv.training.citas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdentityCompletionIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.jwt.access-secret", () -> "a".repeat(40));
        registry.add("app.jwt.refresh-secret", () -> "b".repeat(40));
        registry.add("app.cookie.secure", () -> false);
        registry.add("app.cookie.same-site", () -> "Lax");
        registry.add("app.password-reset.expose-token", () -> true);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recoveryIsGenericSingleUseAndProfileIsOwned() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "identity-" + suffix + "@example.test";
        Long planId = jdbc.queryForObject("select id from eps_plans where active=true limit 1", Long.class);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Ana","lastName":"Inicial","documentType":"CC","documentNumber":"%s","email":"%s","phone":"3000000000","password":"OldPass123!","insurancePlanId":%d}
                """.formatted(suffix, email, planId))).andExpect(status().isCreated());

        String access = login(email, "OldPass123!");
        mvc.perform(get("/api/v1/user/profile").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.affiliation.plan.id").value(planId));
        mvc.perform(patch("/api/v1/user/profile").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ana María","phone":"3010000000"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Ana María"));

        String response = mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(response).get("devToken").asText();
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"NewPass123!"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());
        login(email, "NewPass123!");
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"ThirdPass123!"}
                                """.formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRecoveryEmailDoesNotRevealAccountExistence() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.devToken").doesNotExist());
    }

    @Test
    void affiliationCanBeReplacedWithoutDuplicatingCurrentRows() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "affiliation-" + suffix + "@example.test";
        Long planId = jdbc.queryForObject("select id from eps_plans where active=true limit 1", Long.class);
        String registration = """
                {"firstName":"Afiliado","lastName":"Prueba","documentType":"CC","documentNumber":"A%s","email":"%s","phone":"3000000000","password":"Pass12345!"}
                """.formatted(suffix, email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isCreated());
        String access = login(email, "Pass12345!");
        mvc.perform(put("/api/v1/user/affiliation").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"planId":%d,"membershipNumber":"M-%s"}
                                """.formatted(planId, suffix)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.id").value(planId));
        Long userId = jdbc.queryForObject("select id from users where email=?", Long.class, email);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from user_insurance_affiliations where user_id=? and is_current=true", Integer.class, userId)).isEqualTo(1);
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode access = mapper.readTree(body).get("accessToken");
        return access.asText();
    }
}
