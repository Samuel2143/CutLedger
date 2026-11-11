package com.cutledger.workspace;

import com.cutledger.bootstrap.AppConfig;
import com.cutledger.web.Problem;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SessionController {
    private final SessionService sessions;
    private final AppConfig config;
    private final boolean demo;
    public SessionController(SessionService sessions,AppConfig config,Environment environment) {
        this.sessions=sessions;this.config=config;this.demo=Arrays.asList(environment.getActiveProfiles()).contains("demo");
    }
    public record Access(@Size(max=200) String accessCode) {}
    @PostMapping("/demo-sessions")
    ResponseEntity<?> create(@Valid @RequestBody Access input,HttpServletRequest request) {
        var existing=sessions.authenticate(WorkspaceFilter.cookie(request));
        if(existing.isPresent()) return ResponseEntity.ok(existing.get());
        String scope=sessions.hash(config.sessionPepper()+":ip:"+request.getRemoteAddr());
        if(!sessions.takeQuota(scope,"SESSION",3,Duration.ofHours(1))) throw new Problem(429,"RATE_LIMITED","Too many attempts. Try again in one hour.");
        if(demo && (input.accessCode()==null || !new BCryptPasswordEncoder().matches(input.accessCode(),config.demoAccessCodeHash())))
            throw new Problem(403,"ACCESS_REJECTED","The access code was not accepted.");
        var created=sessions.create();
        var cookie=ResponseCookie.from("cl_session",created.token()).httpOnly(true).secure("https".equals(config.publicBaseUrl().getScheme()))
            .sameSite("Lax").path("/").maxAge(Duration.ofDays(7)).build();
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE,cookie.toString()).body(created.session());
    }
    @GetMapping("/session") SessionService.Session session(@RequestAttribute("workspaceSession") SessionService.Session session) {return session;}
    @GetMapping("/campaigns/{id}") Map<String,Object> campaign(@PathVariable UUID id,@RequestAttribute("workspaceSession") SessionService.Session session) {return sessions.campaign(session,id);}
    @GetMapping("/diagnostics") Map<String,Object> diagnostics() {
        return Map.of("environment","Sandbox","implementationPhase",0,
            "payments",config.paypalConfigured()?"CONFIGURED_NOT_VERIFIED":"NOT_CONFIGURED",
            "interpretation",config.aiConfigured()?"REAL_GEMINI_STRUCTURED_OUTPUT":"NOT_CONFIGURED",
            "checkoutAvailable",config.paypalConfigured(),"aiAvailable",config.aiConfigured(),"accessRequired",demo);
    }
}
