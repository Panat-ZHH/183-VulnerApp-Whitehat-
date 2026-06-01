package ch.bbw.m183.vulnerapp.service;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

// Form Login schickt standardmässig einen Redirect zurück – das passt nicht für eine REST API.
// Dieser Service ersetzt die Default-Handler durch direkte JSON-Antworten.
@Service
@RequiredArgsConstructor
public class RestfulFormService {

	private final UserService userService;

	private final ObjectMapper objectMapper;

	public Customizer<FormLoginConfigurer<HttpSecurity>> restfulFormLogin() {
		return form -> form
				.failureHandler((req, res, ex) -> {
					res.setStatus(HttpServletResponse.SC_FORBIDDEN);
					res.setContentType("application/json");
					res.getWriter().write("{\"error\":\"Bad credentials\"}");
				})
				.successHandler((request, response, auth) -> {
					response.setStatus(HttpServletResponse.SC_OK);
					response.setContentType("application/json");
					response.getWriter().write(objectMapper.writeValueAsString(userService.whoami(auth.getName())));
				});
	}

	public Customizer<ExceptionHandlingConfigurer<HttpSecurity>> unauthorizedPerDefault() {
		return ex -> ex.defaultAuthenticationEntryPointFor(
				new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
				request -> request.getRequestURI().startsWith("/api/")
		);
	}
}
