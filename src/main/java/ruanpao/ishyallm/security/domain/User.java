package ruanpao.ishyallm.security.domain;

import org.springframework.data.annotation.Id;
import ruanpao.ishyallm.common.domain.UserRole;

public class User {

    @Id
    private Long id;
    private String username;
    private String password;
    private String name;
    private String department;
    private UserRole role;
    private boolean enabled;

    public User() {}

    public User(String username, String password, String name, String department, UserRole role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.department = department;
        this.role = role;
        this.enabled = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
