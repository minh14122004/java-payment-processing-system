package com.example.payment.config;

import com.example.payment.exception.*;
import com.example.payment.repository.UserRepository;
import com.example.payment.security.CredentialVersionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;
import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean public SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean public CsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository(); repository.setHeaderName("X-CSRF-TOKEN"); return repository;
    }
    @Bean public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, ObjectMapper mapper,
            SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        http.httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).logout(logout -> logout.disable())
            .requestCache(cache -> cache.disable())
            .securityContext(context -> context.securityContextRepository(contexts))
            .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(routes -> routes.requestMatchers(CredentialVersionFilter::publicRoute).permitAll().anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,ex) -> ApiErrors.write(mapper,res,ApiException.authentication()))
                .accessDeniedHandler((req,res,ex) -> ApiErrors.write(mapper,res, ex instanceof CsrfException
                    ? new ApiException(403,"CSRF_INVALID","Invalid CSRF token") : new ApiException(403,"ACCESS_DENIED","Access denied"))))
            .addFilterAfter(new CredentialVersionFilter(users, mapper), SecurityContextHolderFilter.class);
        return http.build();
    }
}
