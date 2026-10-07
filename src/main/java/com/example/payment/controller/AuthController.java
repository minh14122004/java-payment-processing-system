package com.example.payment.controller;

import com.example.payment.dto.AuthDtos.*;
import com.example.payment.enums.OtpPurpose;
import com.example.payment.security.AuthPrincipal;
import com.example.payment.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final OtpService otp;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    public AuthController(AuthService auth, OtpService otp, SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.auth=auth; this.otp=otp; this.contexts=contexts; this.sessions=sessions;
    }
    @GetMapping("/csrf") public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getToken(), token.getHeaderName()); }
    @PostMapping("/register") public ResponseEntity<Challenge> register(@Valid @RequestBody Register body) {
        return ResponseEntity.accepted().body(otp.register(body));
    }
    @PostMapping("/register/verify") public ResponseEntity<Registration> verify(@Valid @RequestBody Verify body) {
        var result = otp.confirmRegistration(body.challengeId(),body.otp());
        return ResponseEntity.created(URI.create("/api/accounts/" + result.account().id())).body(result);
    }
    @PostMapping("/login") public UserView login(@Valid @RequestBody Login body, HttpServletRequest request, HttpServletResponse response) {
        var user = auth.login(body.email(),body.password());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(new AuthPrincipal(user.getId(),user.getCredentialVersion()), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        sessions.onAuthentication(authentication,request,response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context); contexts.saveContext(context,request,response);
        return UserView.of(user);
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password-change/request") public ResponseEntity<Challenge> change(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ChangeRequest body) {
        return ResponseEntity.accepted().body(otp.requestChange(principal,body.currentPassword()));
    }
    @PostMapping("/password-reset/request") public ResponseEntity<Challenge> reset(@Valid @RequestBody ResetRequest body) {
        return ResponseEntity.accepted().body(otp.requestReset(body.email()));
    }
    @PostMapping("/password-change/confirm") public ResponseEntity<Void> confirmChange(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PasswordConfirm body, HttpServletRequest request, HttpServletResponse response) {
        otp.confirmPassword(OtpPurpose.PASSWORD_CHANGE,principal,body);
        return logout(request,response);
    }
    @PostMapping("/password-reset/confirm") public ResponseEntity<Void> confirmReset(@Valid @RequestBody PasswordConfirm body,
            HttpServletRequest request, HttpServletResponse response) {
        otp.confirmPassword(OtpPurpose.PASSWORD_RESET,null,body);
        return logout(request,response);
    }
    @PostMapping("/otp/resend") public ResponseEntity<Challenge> resend(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody Resend body) {
        return ResponseEntity.accepted().body(otp.resend(body.challengeId(),principal));
    }
}
