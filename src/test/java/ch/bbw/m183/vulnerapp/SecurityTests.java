package ch.bbw.m183.vulnerapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityTests {

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@BeforeEach
	void setup() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	// --- helpers ---

	private String getCsrfToken() {
		ResponseCookie xsrf = client.get().uri("/api/blog")
				.exchange()
				.returnResult(String.class)
				.getResponseCookies()
				.getFirst("XSRF-TOKEN");
		return xsrf != null ? xsrf.getValue() : "";
	}

	private record Session(String jsessionid, String csrfToken) {}

	private Session loginAs(String username, String password) {
		String csrf = getCsrfToken();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", username);
		form.add("password", password);

		var result = client.post().uri("/api/user/login")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.cookie("XSRF-TOKEN", csrf)
				.header("X-XSRF-TOKEN", csrf)
				.bodyValue(form)
				.exchange()
				.expectStatus().isOk()
				.returnResult(String.class);

		ResponseCookie session = result.getResponseCookies().getFirst("JSESSIONID");
		return new Session(session != null ? session.getValue() : "", csrf);
	}

	// --- GET / ---

	@Test
	void rootIsPublic() {
		client.get().uri("/").exchange().expectStatus().isOk();
	}

	@Test
	void rootIsAccessibleAuthenticated() {
		Session s = loginAs("user", "User@Secure123!");
		client.get().uri("/")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange().expectStatus().isOk();
	}

	// --- GET /api/blog ---

	@Test
	void getBlogsIsPublic() {
		client.get().uri("/api/blog").exchange().expectStatus().isOk();
	}

	@Test
	void getBlogsIsAccessibleAuthenticated() {
		Session s = loginAs("user", "User@Secure123!");
		client.get().uri("/api/blog")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange().expectStatus().isOk();
	}

	// --- POST /api/blog ---

	@Test
	void postBlogBlockedForAnonymous() {
		client.post().uri("/api/blog")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"title\":\"x\",\"body\":\"y\"}")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void postBlogBlockedWithoutCsrf() {
		// authenticated but no CSRF token in header → 403
		Session s = loginAs("user", "User@Secure123!");
		client.post().uri("/api/blog")
				.contentType(MediaType.APPLICATION_JSON)
				.cookie("JSESSIONID", s.jsessionid())
				.bodyValue("{\"title\":\"x\",\"body\":\"y\"}")
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void postBlogAllowedWithCsrf() {
		Session s = loginAs("user", "User@Secure123!");
		String csrf = getCsrfToken();
		client.post().uri("/api/blog")
				.contentType(MediaType.APPLICATION_JSON)
				.cookie("JSESSIONID", s.jsessionid())
				.cookie("XSRF-TOKEN", csrf)
				.header("X-XSRF-TOKEN", csrf)
				.bodyValue("{\"title\":\"Security Test\",\"body\":\"Test body content\"}")
				.exchange()
				.expectStatus().isCreated();
	}

	@Test
	void postBlogAllowedForAdminWithCsrf() {
		Session s = loginAs("admin", "Admin@Secure123!");
		String csrf = getCsrfToken();
		client.post().uri("/api/blog")
				.contentType(MediaType.APPLICATION_JSON)
				.cookie("JSESSIONID", s.jsessionid())
				.cookie("XSRF-TOKEN", csrf)
				.header("X-XSRF-TOKEN", csrf)
				.bodyValue("{\"title\":\"Admin Blog\",\"body\":\"Admin content\"}")
				.exchange()
				.expectStatus().isCreated();
	}

	// --- GET /api/user/whoami ---

	@Test
	void whoamiBlockedForAnonymous() {
		client.get().uri("/api/user/whoami")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void whoamiAllowedForAuthenticatedUser() {
		Session s = loginAs("user", "User@Secure123!");
		client.get().uri("/api/user/whoami")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.username").isEqualTo("user")
				.jsonPath("$.password").doesNotExist(); // password must NOT be in response
	}

	@Test
	void whoamiAllowedForAdmin() {
		Session s = loginAs("admin", "Admin@Secure123!");
		client.get().uri("/api/user/whoami")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.username").isEqualTo("admin");
	}

	// --- /api/admin/* ---

	@Test
	void adminEndpointBlockedForAnonymous() {
		client.get().uri("/api/admin/users")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void adminEndpointBlockedForRegularUser() {
		Session s = loginAs("user", "User@Secure123!");
		client.get().uri("/api/admin/users")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void adminEndpointAllowedForAdmin() {
		Session s = loginAs("admin", "Admin@Secure123!");
		client.get().uri("/api/admin/users")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange()
				.expectStatus().isOk();
	}

	// --- GET /actuator/health ---

	@Test
	void healthIsPublicWithoutDetails() {
		client.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP")
				.jsonPath("$.components").doesNotExist(); // no details for anonymous
	}

	@Test
	void healthShowsDetailsForAuthenticatedUser() {
		Session s = loginAs("user", "User@Secure123!");
		client.get().uri("/actuator/health")
				.cookie("JSESSIONID", s.jsessionid())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP")
				.jsonPath("$.components").exists(); // details visible when authenticated
	}

	// --- input validation ---

	@Test
	void blogWithEmptyTitleIsRejected() {
		Session s = loginAs("user", "User@Secure123!");
		String csrf = getCsrfToken();
		client.post().uri("/api/blog")
				.contentType(MediaType.APPLICATION_JSON)
				.cookie("JSESSIONID", s.jsessionid())
				.cookie("XSRF-TOKEN", csrf)
				.header("X-XSRF-TOKEN", csrf)
				.bodyValue("{\"title\":\"\",\"body\":\"body\"}")
				.exchange()
				.expectStatus().isBadRequest();
	}

	// --- login ---

	@Test
	void loginWithWrongPasswordFails() {
		String csrf = getCsrfToken();
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", "user");
		form.add("password", "wrongpassword");

		client.post().uri("/api/user/login")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.cookie("XSRF-TOKEN", csrf)
				.header("X-XSRF-TOKEN", csrf)
				.bodyValue(form)
				.exchange()
				.expectStatus().isForbidden();
	}
}
