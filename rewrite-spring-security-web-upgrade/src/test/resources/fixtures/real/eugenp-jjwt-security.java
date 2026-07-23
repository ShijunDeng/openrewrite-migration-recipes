// Adapted from eugenp/tutorials at 5e4114a9482d68b6766ca738c087f0f9a87a7bd2.
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

class RealJjwtSecurityFixture {
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterAfter(new JwtCsrfValidatorFilter(), CsrfFilter.class)
                .csrf()
                .ignoringAntMatchers("/dynamic-builder-general", "/set-secrets")
                .and()
                .authorizeRequests()
                .antMatchers("/**")
                .permitAll();
        return http.build();
    }

    private static class JwtCsrfValidatorFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            chain.doFilter(request, response);
        }
    }
}
