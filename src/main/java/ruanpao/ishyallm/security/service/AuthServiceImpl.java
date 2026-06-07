package ruanpao.ishyallm.security.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.domain.User;
import ruanpao.ishyallm.security.dto.LoginResponse;
import ruanpao.ishyallm.security.jwt.JwtTokenProvider;
import ruanpao.ishyallm.security.repository.UserRepository;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<User> register(String username, String password, String name, String department, UserRole role) {
        return userRepository.existsByUsername(username)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Username already exists: " + username));
                    }
                    User user = new User(username, passwordEncoder.encode(password), name, department, role);
                    return userRepository.save(user);
                });
    }

    @Override
    public Mono<LoginResponse> login(String username, String password) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + username)))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new IllegalArgumentException("Invalid password"));
                    }
                    String token = jwtTokenProvider.generateToken(
                            String.valueOf(user.getId()),
                            user.getDepartment(),
                            user.getRole()
                    );
                    return Mono.just(new LoginResponse(token, user));
                });
    }
}
