package ruanpao.ishyallm.security.service;

import reactor.core.publisher.Mono;
import ruanpao.ishyallm.common.domain.UserRole;
import ruanpao.ishyallm.security.domain.User;
import ruanpao.ishyallm.security.dto.LoginResponse;

public interface AuthService {

    Mono<User> register(String username, String password, String name, String department, UserRole role);

    Mono<LoginResponse> login(String username, String password);
}
