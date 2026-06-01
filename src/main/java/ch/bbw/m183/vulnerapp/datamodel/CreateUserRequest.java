package ch.bbw.m183.vulnerapp.datamodel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Size(max = 100) String fullname,
		// min 12 chars, uppercase, lowercase, digit, special character
		@NotBlank @Pattern(
				regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&]).{12,}$",
				message = "Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character (@$!%*?&)"
		) String password
) {
}
