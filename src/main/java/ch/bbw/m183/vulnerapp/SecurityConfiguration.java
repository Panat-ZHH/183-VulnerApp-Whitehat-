package ch.bbw.m183.vulnerapp;

import java.io.IOException;

import ch.bbw.m183.vulnerapp.repository.UserRepository;
import ch.bbw.m183.vulnerapp.service.RestfulFormService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Bean
	public UserDetailsService userDetailsService(UserRepository userRepository) {
		return username -> {
			var user = userRepository.findById(username)
					.orElseThrow(() -> new UsernameNotFoundException("unknown user: " + username));
			var roles = (user.getRoles() != null && !user.getRoles().isBlank())
					? user.getRoles().split(",")
					: new String[]{"ROLE_USER"};
			return User.withUsername(user.getUsername())
					.password(user.getPassword())
					.authorities(roles)
					.build();
		};
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, RestfulFormService restfulFormService) throws Exception {
		return http
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
				)
				.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/index.html", "/script.js", "/robots.txt").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/blog").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated()
				)
				.formLogin(form -> {
					restfulFormService.restfulFormLogin().customize(form);
					form.loginProcessingUrl("/api/user/login");
				})
				.logout(logout -> logout
						.logoutUrl("/api/user/logout")
						.logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
						.deleteCookies("JSESSIONID")
				)
				.exceptionHandling(restfulFormService.unauthorizedPerDefault())
				.build();
	}

	// Spring generiert den CSRF-Token lazy – dieser Filter erzwingt die Generierung sofort,
	// damit das XSRF-TOKEN Cookie schon beim ersten Request im Browser ankommt.
	static final class CsrfCookieFilter extends OncePerRequestFilter {

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}
	}
}
