// Adapted from eugenp/tutorials at 5e4114a9482d68b6766ca738c087f0f9a87a7bd2.
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

class RealHttpClientSecurityFixture {
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/securityNone")
                .permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .httpBasic();
        http.addFilterAfter(new CustomFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static class CustomFilter extends UsernamePasswordAuthenticationFilter {
    }
}
