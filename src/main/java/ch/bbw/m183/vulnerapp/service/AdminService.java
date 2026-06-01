package ch.bbw.m183.vulnerapp.service;

import java.util.stream.Stream;

import ch.bbw.m183.vulnerapp.datamodel.CreateUserRequest;
import ch.bbw.m183.vulnerapp.datamodel.UserEntity;
import ch.bbw.m183.vulnerapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	public UserEntity createUser(CreateUserRequest request) {
		if (userRepository.existsById(request.username())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
		}
		var user = new UserEntity()
				.setUsername(request.username())
				.setFullname(request.fullname())
				.setPassword(passwordEncoder.encode(request.password()))
				.setRoles("ROLE_USER");
		return userRepository.save(user);
	}

	public Page<UserEntity> getUsers(Pageable pageable) {
		return userRepository.findAll(pageable);
	}

	public void deleteUser(String username) {
		userRepository.deleteById(username);
	}

	@EventListener(ContextRefreshedEvent.class)
	public void loadTestUsers() {
		Stream.of(
				new UserEntity().setUsername("admin").setFullname("Super Admin")
						.setPassword(passwordEncoder.encode("Admin@Secure123!"))
						.setRoles("ROLE_ADMIN,ROLE_USER"),
				new UserEntity().setUsername("user").setFullname("Johanna Doe")
						.setPassword(passwordEncoder.encode("User@Secure123!"))
						.setRoles("ROLE_USER"))
				.filter(u -> !userRepository.existsById(u.getUsername()))
				.forEach(userRepository::save);
	}
}
