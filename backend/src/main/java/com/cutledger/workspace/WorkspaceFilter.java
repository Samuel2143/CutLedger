package com.cutledger.workspace;

import com.cutledger.bootstrap.AppConfig;
import com.cutledger.web.Problem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-110)
public class WorkspaceFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final AppConfig config;
    private final ObjectMapper json;
    public WorkspaceFilter(SessionService sessions, AppConfig config, ObjectMapper json) { this.sessions=sessions; this.config=config; this.json=json; }
    public static String cookie(HttpServletRequest request) {
        if(request.getCookies()!=null) for(var cookie:request.getCookies()) if(cookie.getName().equals("cl_session")) return cookie.getValue();
        return null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String trace=UUID.randomUUID().toString(); request.setAttribute("traceId",trace); response.setHeader("X-Request-Id",trace);
        String path=request.getRequestURI();
        if(!path.startsWith("/api/")) { chain.doFilter(request,response); return; }
        response.setHeader("Cache-Control","no-store");
        try {
            boolean webhook=path.equals("/api/v1/webhooks/paypal");
            boolean mutation=!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
            long max=webhook?262144:65536;
            if(request.getContentLengthLong()>max) throw new Problem(413,"BODY_TOO_LARGE","The request is too large.");
            if(mutation && !webhook && !config.origin().equals(request.getHeader("Origin"))) throw new Problem(403,"ORIGIN_REJECTED","Open CutLedger from its configured origin.");
            boolean publicPath=Set.of("/api/v1/demo-sessions","/api/v1/diagnostics","/api/v1/webhooks/paypal").contains(path);
            if(!publicPath) {
                var session=sessions.authenticate(cookie(request)).orElseThrow(()->new Problem(401,"SESSION_REQUIRED","Open or resume your sample campaign."));
                request.setAttribute("workspaceSession",session);
                if(mutation) {
                    String csrf=request.getHeader("X-CSRF-Token");
                    if(csrf==null || !MessageDigest.isEqual(session.csrfToken().getBytes(StandardCharsets.UTF_8),csrf.getBytes(StandardCharsets.UTF_8)))
                        throw new Problem(403,"CSRF_REJECTED","Reload your session before trying again.");
                }
            }
            // Bound chunked bodies too; reject before Jackson or webhook parsing can consume them.
            if(mutation) {
                byte[] body=request.getInputStream().readNBytes((int)max+1);
                if(body.length>max) throw new Problem(413,"BODY_TOO_LARGE","The request is too large.");
                request=new BufferedRequest(request,body);
            }
            chain.doFilter(request,response);
        } catch(Problem p) {
            response.setStatus(p.status); response.setContentType("application/problem+json");
            json.writeValue(response.getOutputStream(),Map.of("status",p.status,"code",p.code,"title",p.code,"detail",p.getMessage(),"traceId",trace));
        }
    }
    private static class BufferedRequest extends HttpServletRequestWrapper {
        final byte[] body;
        BufferedRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public ServletInputStream getInputStream(){
            var input=new ByteArrayInputStream(body);
            return new ServletInputStream(){
                @Override public int read(){return input.read();}
                @Override public boolean isFinished(){return input.available()==0;}
                @Override public boolean isReady(){return true;}
                @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
            };
        }
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
