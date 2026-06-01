package ch.bbw.m183.vulnerapp.datamodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	@NotBlank
	@Size(min = 3, max = 50)
	String username;

	@Column
	@NotBlank
	@Size(max = 100)
	String fullname;

	@Column
	@JsonIgnore
	String password;

	@Column
	String roles;
}
