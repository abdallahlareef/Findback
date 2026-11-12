package edu.findback.auth;

import edu.findback.user.User;
import edu.findback.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final AuthService authService;

        private final AuthenticationManager authenticationManager;
        private final SecurityContextRepository securityContextRepository;
        private final UserRepository userRepository;

        public AuthController(
                        AuthService authService,
                        AuthenticationManager authenticationManager,
                        SecurityContextRepository securityContextRepository,
                        UserRepository userRepository) {
                this.authService = authService;
                this.authenticationManager = authenticationManager;
                this.securityContextRepository = securityContextRepository;
                this.userRepository = userRepository;
        }

        @PostMapping("/register")
        public ResponseEntity<RegisterResponse> register(
                        @Valid @RequestBody RegisterRequest request) {

                User user = authService.register(request);

                RegisterResponse response = new RegisterResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getRole().name());

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @PostMapping("/login")
        public LoginResponse login(
                        @Valid @RequestBody LoginRequest loginRequest,
                        HttpServletRequest request,
                        HttpServletResponse response) {

                String email = loginRequest.email()
                                .trim()
                                .toLowerCase();

                UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken
                                .unauthenticated(
                                                email,
                                                loginRequest.password());

                Authentication authentication = authenticationManager.authenticate(token);

                SecurityContext context = SecurityContextHolder.createEmptyContext();

                context.setAuthentication(authentication);

                SecurityContextHolder.setContext(context);

                securityContextRepository.saveContext(
                                context,
                                request,
                                response);

                var user = userRepository
                                .findByEmail(email)
                                .orElseThrow();

                return new LoginResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getRole().name());
        }

        @GetMapping("/me")
        public LoginResponse me(Authentication authentication) {

                String email = authentication.getName();

                var user = userRepository
                                .findByEmail(email)
                                .orElseThrow();

                return new LoginResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getRole().name());
        }
}