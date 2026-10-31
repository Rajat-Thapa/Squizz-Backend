package com.squizz.subscription;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class SubscriptionService {
 private final JdbcTemplate db; private final String publicUrl; private final boolean confirmationEnabled;
 public SubscriptionService(JdbcTemplate db,@Value("${app.public-url}") String publicUrl,@Value("${app.confirmation-enabled:false}") boolean confirmationEnabled) { this.db=db;this.publicUrl=publicUrl.replaceAll("/$","");this.confirmationEnabled=confirmationEnabled; }
 static String hash(String token) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
 static String token() { return UUID.randomUUID().toString()+UUID.randomUUID().toString(); }
 @Transactional
 public void subscribe(SubscriptionRequest r) {
  String email=r.email().trim().toLowerCase(Locale.ROOT); OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
  var rows=db.queryForList("SELECT id,status,updated_at FROM subscribers WHERE email=? FOR UPDATE",email);
  // Capture unverified addresses without creating mail jobs or changing existing subscriptions.
  if(!confirmationEnabled) {
   if(rows.isEmpty()) {
    String interests=r.interests().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    db.update("INSERT INTO subscribers(id,email,interests,status,consent_version,consent_at,unsubscribe_hash,created_at,updated_at) VALUES (?,?,?,'PENDING','launch-v1',?,?,?,?)",UUID.randomUUID(),email,interests,now,hash(token()),now,now);
   }
   return;
  }
  UUID id=UUID.randomUUID();
  if(!rows.isEmpty()) {
   var row=rows.get(0);id=(UUID)row.get("id");
   if("ACTIVE".equals(row.get("status")))return;
   OffsetDateTime updated=db.queryForObject("SELECT updated_at FROM subscribers WHERE id=?",OffsetDateTime.class,id);
   if(updated!=null && updated.isAfter(now.minusMinutes(5)))return;
  }
  String confirm=token(),unsubscribe=token();
  String interests=r.interests().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  if(rows.isEmpty()) db.update("INSERT INTO subscribers(id,email,interests,status,consent_version,consent_at,confirmation_hash,confirmation_expires_at,unsubscribe_hash,created_at,updated_at) VALUES (?,?,?,'PENDING','launch-v1',?,?,?,?,?,?)",id,email,interests,now,hash(confirm),now.plusHours(24),hash(unsubscribe),now,now);
  else {db.update("DELETE FROM email_outbox WHERE subscriber_id=?",id);db.update("UPDATE subscribers SET interests=?,status='PENDING',consent_at=?,consent_version='launch-v1',confirmed_at=NULL,confirmation_hash=?,confirmation_expires_at=?,unsubscribe_hash=?,updated_at=? WHERE id=?",interests,now,hash(confirm),now.plusHours(24),hash(unsubscribe),now,id);}
  String body="Good things come in a squeeze.\n\nYou asked to hear about Squizz launch and preorders. Confirm your email here (link expires in 24 hours):\n"+publicUrl+"/#/confirm?token="+confirm+"\n\nInterested in: "+interests+"\n\nThis is a free interest list, not an order. If you didn’t request this, ignore this email.\n\nCancel this signup or unsubscribe: "+publicUrl+"/#/unsubscribe?token="+unsubscribe+"\n\nSquizz";
  db.update("INSERT INTO email_outbox(id,subscriber_id,recipient,subject,body,next_attempt_at,created_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),id,email,"One more little thing — confirm your Squizz email",body,now,now);
 }
 @Transactional public void confirm(String token) {
  String h=hash(token); OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC);
  int changed=db.update("UPDATE subscribers SET status='ACTIVE',confirmed_at=?,updated_at=? WHERE confirmation_hash=? AND status='PENDING' AND confirmation_expires_at>?",now,now,h,now);
  if(changed==0 && Boolean.FALSE.equals(db.queryForObject("SELECT COUNT(*)>0 FROM subscribers WHERE confirmation_hash=? AND status='ACTIVE'",Boolean.class,h)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired link");
 }
 @Transactional public void unsubscribe(String token) {
  String h=hash(token);
  db.update("DELETE FROM email_outbox WHERE subscriber_id IN (SELECT id FROM subscribers WHERE unsubscribe_hash=?)",h);
  db.update("UPDATE subscribers SET status='UNSUBSCRIBED',updated_at=? WHERE unsubscribe_hash=?",OffsetDateTime.now(ZoneOffset.UTC),h);
 }
}
