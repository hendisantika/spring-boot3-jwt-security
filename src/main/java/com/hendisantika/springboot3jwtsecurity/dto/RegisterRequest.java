package com.hendisantika.springboot3jwtsecurity.dto;

import com.hendisantika.springboot3jwtsecurity.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot3-jwt-security
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 6/14/23
 * Time: 06:47
 * To change this template use File | Settings | File Templates.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "firstname is required")
    private String firstname;

    @NotBlank(message = "lastname is required")
    private String lastname;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    private String email;

    @NotBlank(message = "password is required")
    private String password;

    /**
     * Optional — registrations without a role become a plain {@link Role#USER}.
     */
    private Role role;
}
