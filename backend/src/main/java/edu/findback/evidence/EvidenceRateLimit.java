package edu.findback.evidence;

import jakarta.servlet.http.*;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Per-account single-server throttle; pair counters remain durable in the database. */
@Component
public class EvidenceRateLimit implements HandlerInterceptor, WebMvcConfigurer {
    private record Window(long start, int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final int limit;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceRateLimit(@Value("${findback.evidence.requests-per-minute:60}") int limit) { this(limit,Clock.systemUTC()); }
    EvidenceRateLimit(int limit,Clock clock) { this.limit=limit;this.clock=clock; }
    synchronized boolean allow(String account) {
        long now=clock.millis();
        windows.entrySet().removeIf(e->now-e.getValue().start()>=60000);
        Window previous=windows.get(account);
        if(previous==null && windows.size()>=10000) return false;
        if(previous!=null && previous.count()>=limit) return false;
        windows.put(account,previous==null?new Window(now,1):new Window(previous.start(),previous.count()+1));
        return true;
    }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        if(request.getUserPrincipal()!=null && !allow(request.getUserPrincipal().getName())) {
            response.setHeader("Retry-After","60"); response.sendError(429,"Please wait before trying again."); return false;
        }
        return true;
    }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(this).addPathPatterns("/api/evidence/**"); }
}
