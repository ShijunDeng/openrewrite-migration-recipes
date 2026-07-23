// Adapted from spring-guides/gs-securing-web at 299296be54569a14ef8e67b25f6193936385e6bf.
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.web.SecurityFilterChain;

class RealSpringGuideSecurityFixture {
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/home").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(LogoutConfigurer::permitAll);
        return http.build();
    }
}
