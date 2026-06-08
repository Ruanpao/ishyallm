package ruanpao.ishyallm.security.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.domain.User;
import ruanpao.ishyallm.security.dto.RegisterRequest;
import ruanpao.ishyallm.security.jwt.JwtTokenProvider;
import ruanpao.ishyallm.security.jwt.TokenClaims;
import ruanpao.ishyallm.security.service.AuthService;

import javax.sql.DataSource;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final DataSource dataSource;

    public AdminController(AuthService authService, JwtTokenProvider jwtTokenProvider, DataSource dataSource) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.dataSource = dataSource;
    }

    @PostMapping("/users")
    public Mono<ResponseEntity<Object>> createUser(@RequestBody RegisterRequest request,
                                                    @RequestHeader("Authorization") String auth) {
        if (!isAdmin(auth)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin only"));
        }
        UserRole userRole = "ADMIN".equalsIgnoreCase(request.role()) ? UserRole.ADMIN : UserRole.DOCTOR;
        return authService.register(request.username(), request.password(),
                        request.name(), request.department(), userRole)
                .map(user -> { user.setPassword(null); return ResponseEntity.ok((Object) user); })
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body((Object) e.getMessage())));
    }

    @GetMapping("/users")
    public Mono<ResponseEntity<Object>> listUsers(@RequestHeader("Authorization") String auth) {
        if (!isAdmin(auth)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin only"));
        }
        return Mono.just(ResponseEntity.ok((Object) Map.of("message", "User list - implement via UserRepository")));
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> stats() {
        String dbStatus = "connected";
        try (var c = dataSource.getConnection()) {
        } catch (Exception e) {
            dbStatus = "disconnected";
        }
        return Mono.just(Map.of(
                "database", dbStatus,
                "status", "running"
        ));
    }

    private boolean isAdmin(String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            TokenClaims claims = jwtTokenProvider.verifyAndExtract(token);
            return claims.role() == UserRole.ADMIN;
        } catch (Exception e) {
            return false;
        }
    }
}
