package edu.findback.evidence;
import edu.findback.user.*;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component
public class EvidenceAccess {
    private final UserRepository users;
    public EvidenceAccess(UserRepository users) { this.users=users; }
    public User current() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(authentication.getName()).filter(User::isActive)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    public User staff() {
        User user=current();
        if(user.getRole()!=Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
}
