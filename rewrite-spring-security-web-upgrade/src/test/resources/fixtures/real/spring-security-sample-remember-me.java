// Adapted from spring-projects/spring-security-samples at 472a9b7cb683e854bc9d9781875b2df72faad7a5.
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

class RealRememberMeSecurityFixture {
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .rememberMe(rememberMe -> rememberMe.userDetailsService(users));
        return http.build();
    }
}
