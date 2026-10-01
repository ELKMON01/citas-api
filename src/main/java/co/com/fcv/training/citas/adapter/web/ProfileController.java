package co.com.fcv.training.citas.adapter.web;

import co.com.fcv.training.citas.application.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
class ProfileController {
    record ProfilePatch(@Size(max = 120) String firstName, @Size(max = 120) String lastName, @Size(max = 40) String phone) {}
    record AffiliationRequest(@NotNull Long planId, @Size(max = 80) String membershipNumber) {}

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    Map<String, Object> profile(Authentication authentication) {
        return profiles.read(user(authentication));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    Map<String, Object> update(Authentication authentication, @Valid @RequestBody ProfilePatch request) {
        return profiles.update(user(authentication), request.firstName(), request.lastName(), request.phone());
    }

    @PutMapping("/affiliation")
    @PreAuthorize("hasRole('USER')")
    Map<String, Object> affiliation(Authentication authentication, @Valid @RequestBody AffiliationRequest request) {
        return profiles.setAffiliation(user(authentication), request.planId(), request.membershipNumber());
    }

    @DeleteMapping("/affiliation")
    @PreAuthorize("hasRole('USER')")
    ResponseEntity<Void> removeAffiliation(Authentication authentication) {
        profiles.removeAffiliation(user(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long user(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
