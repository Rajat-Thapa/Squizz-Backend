package com.squizz.notification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
@RestController
public class NotificationController {
 private final JavaMailSender mail;private final String key,from;
 public NotificationController(JavaMailSender mail,@Value("${app.internal-api-key}")String key,@Value("${app.mail-from}")String from){if(key.length()<32)throw new IllegalArgumentException("INTERNAL_API_KEY must have at least 32 characters");this.mail=mail;this.key=key;this.from=from;}
 public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254)String recipient,@NotBlank @Size(max=160) @Pattern(regexp="[^\r\n]+")String subject,@NotBlank @Size(max=10000)String body){}
 @PostMapping("/internal/emails") public ResponseEntity<Void> send(@RequestHeader(value="X-Internal-Key",defaultValue="")String supplied,@Valid @RequestBody Email email){
  if(!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))return ResponseEntity.status(401).build();
  var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email.recipient());message.setSubject(email.subject());message.setText(email.body());
  try{mail.send(message);return ResponseEntity.noContent().build();}catch(MailException e){return ResponseEntity.status(503).build();}
 }
}
