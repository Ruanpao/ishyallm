package ruanpao.ishyallm.security.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.dto.LoginRequest;
import ruanpao.ishyallm.security.dto.RegisterRequest;
import ruanpao.ishyallm.security.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Object>> register(@RequestBody RegisterRequest request) {
        if (isInvalid(request)) {
            return Mono.just(ResponseEntity.badRequest().body((Object) "Missing required fields"));
        }

        UserRole role = UserRole.DOCTOR;
        if (request.role() != null && request.role().equalsIgnoreCase("ADMIN")) {
            role = UserRole.ADMIN;
        }

        return authService.register(request.username(), request.password(),
                        request.name(), request.department(), role)
                .<Object>map(user -> {
                    user.setPassword(null);
                    return user;
                })
                .map(body -> ResponseEntity.ok(body))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().body((Object) e.getMessage())));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body((Object) "Missing required fields"));
        }

        return authService.login(request.username(), request.password())
                .<Object>map(loginResponse -> loginResponse)
                .map(body -> ResponseEntity.ok(body))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((Object) e.getMessage())));
    }

    private boolean isInvalid(RegisterRequest request) {
        return request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.name() == null || request.name().isBlank();
    }
}
