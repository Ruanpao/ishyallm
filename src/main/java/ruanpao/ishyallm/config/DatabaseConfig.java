package ruanpao.ishyallm.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://localhost:5432/ishyallm");
        cfg.setUsername("postgres");
        cfg.setPassword("114514");
        cfg.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(cfg);
    }
}
