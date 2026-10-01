package co.com.fcv.training.citas.application;

import co.com.fcv.training.citas.domain.Identity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Service
public class PasswordRecoveryService {
    public record RequestResult(boolean accepted, String developmentToken) {}
    private record ResetToken(Long id, Long userId, LocalDateTime expiresAt) {}

    private final JdbcTemplate jdbc;
    private final Ports.Passwords passwords;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(JdbcTemplate jdbc, Ports.Passwords passwords, Clock clock) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public RequestResult request(String email, boolean exposeDevelopmentToken) {
        String normalized = Identity.email(email);
        var userIds = jdbc.query("select id from users where email=? and active=true", (rs, row) -> rs.getLong(1), normalized);
        if (userIds.isEmpty()) return new RequestResult(true, null);

        Long userId = userIds.getFirst();
        LocalDateTime now = now();
        jdbc.update("update password_reset_tokens set used_at=? where user_id=? and used_at is null", now, userId);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(32));
        jdbc.update("insert into password_reset_tokens(user_id,token_hash,expires_at) values (?,?,?)",
                userId, AuthService.hash(rawToken), now.plusMinutes(30));
        return new RequestResult(true, exposeDevelopmentToken ? rawToken : null);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) throw new AuthFailure();
        if (newPassword == null || newPassword.isBlank()) throw new IllegalArgumentException("Contraseña obligatoria");
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Contraseña demasiado larga");
        }

        LocalDateTime now = now();
        ResetToken token = jdbc.query("select id,user_id,expires_at from password_reset_tokens where token_hash=? and used_at is null for update",
                (rs, row) -> new ResetToken(rs.getLong("id"), rs.getLong("user_id"), rs.getObject("expires_at", LocalDateTime.class)),
                AuthService.hash(rawToken)).stream().findFirst().orElseThrow(AuthFailure::new);
        if (token.expiresAt().isBefore(now)) throw new AuthFailure();

        Long userId = token.userId();
        jdbc.update("update users set password_hash=?,updated_at=? where id=? and active=true", passwords.hash(newPassword), now, userId);
        jdbc.update("update password_reset_tokens set used_at=? where id=?", now, token.id());
        jdbc.update("update refresh_tokens set revoked_at=? where user_id=? and revoked_at is null", now, userId);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("America/Bogota"));
    }
}
