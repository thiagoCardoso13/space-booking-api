package com.thiago.spacebooking.auth;

import com.thiago.spacebooking.common.EmailAlreadyUsedException;
import com.thiago.spacebooking.common.InvalidCredentialsException;
import com.thiago.spacebooking.user.Role;
import com.thiago.spacebooking.user.User;
import com.thiago.spacebooking.user.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the email is unknown, so unknown-email and wrong-password take similar time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), Role.USER);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent sign-ups with the same email: the unique constraint decides.
            throw new EmailAlreadyUsedException();
        }
    }

    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return jwtService.issueToken(user);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
