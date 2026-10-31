package com.squizz.subscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
 private final SubscriptionService service;private final EmailRateLimiter limiter;
 public SubscriptionController(SubscriptionService service,EmailRateLimiter limiter){this.service=service;this.limiter=limiter;}
 public record TokenRequest(@NotBlank @Pattern(regexp="[a-fA-F0-9-]{72}") String token) {}
 @PostMapping public ResponseEntity<?> subscribe(@Valid @RequestBody SubscriptionRequest request){
  if(request.website()!=null&&!request.website().isBlank())return accepted();
  if(!limiter.allow(request.email()))return ResponseEntity.status(429).header("Retry-After","3600").body(Map.of("message","Please try again later."));
  try{service.subscribe(request);}catch(DuplicateKeyException ignored){/* Concurrent signup already inserted the same email. */}
  return accepted();
 }
 private ResponseEntity<?> accepted(){return ResponseEntity.accepted().body(Map.of("message","Thanks for joining the Squizz waitlist."));}
 @PostMapping("/confirm") public ResponseEntity<?> confirm(@Valid @RequestBody TokenRequest request){service.confirm(request.token());return ResponseEntity.ok(Map.of("message","Email confirmed."));}
 @PostMapping("/unsubscribe") public ResponseEntity<?> unsubscribe(@Valid @RequestBody TokenRequest request){service.unsubscribe(request.token());return ResponseEntity.ok(Map.of("message","Unsubscribed."));}
}
