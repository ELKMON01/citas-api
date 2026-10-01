package co.com.fcv.training.citas.adapter.web;

import co.com.fcv.training.citas.application.PasswordRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
class PasswordRecoveryController {
    record ForgotRequest(@NotBlank @Email String email) {}
    record ResetRequest(@NotBlank String token, @NotBlank String newPassword) {}

    private final PasswordRecoveryService recovery;
    private final boolean exposeDevelopmentToken;

    PasswordRecoveryController(PasswordRecoveryService recovery,
                               @Value("${app.password-reset.expose-token:false}") boolean exposeDevelopmentToken) {
        this.recovery = recovery;
        this.exposeDevelopmentToken = exposeDevelopmentToken;
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Map<String, Object>> forgot(@Valid @RequestBody ForgotRequest request) {
        PasswordRecoveryService.RequestResult result = recovery.request(request.email(), exposeDevelopmentToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Si la cuenta existe, recibirás instrucciones para recuperar el acceso.");
        if (result.developmentToken() != null) body.put("devToken", result.developmentToken());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest request) {
        recovery.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
