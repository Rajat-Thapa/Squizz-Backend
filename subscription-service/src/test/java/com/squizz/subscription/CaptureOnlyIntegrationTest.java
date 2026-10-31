package com.squizz.subscription;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.confirmation-enabled=false","app.outbox-enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class CaptureOnlyIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired ApplicationContext context;
 @BeforeEach void clear(){db.update("DELETE FROM email_outbox");db.update("DELETE FROM subscribers");}
 String address(){return UUID.randomUUID()+"@example.com";}
 void signup(String email)throws Exception{
  mvc.perform(post("/api/v1/subscriptions").contentType("application/json")
   .content("{\"email\":\""+email+"\",\"interests\":[\"VOLT\"],\"consent\":true,\"website\":\"\"}"))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value("Thanks for joining the Squizz waitlist."));
 }
 @Test void capturesAndDeduplicatesWithoutConfirmationOrMail()throws Exception{
  String email=address().toUpperCase(Locale.ROOT);signup(email);signup(email.toLowerCase(Locale.ROOT));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM subscribers",Integer.class)).isEqualTo(1);
  var row=db.queryForMap("SELECT * FROM subscribers");
  assertThat(row.get("email")).isEqualTo(email.toLowerCase(Locale.ROOT));
  assertThat(row.get("interests")).isEqualTo("VOLT");
  assertThat(row.get("status")).isEqualTo("PENDING");
  assertThat(row.get("consent_at")).isNotNull();
  assertThat(row.get("confirmation_hash")).isNull();
  assertThat(row.get("confirmation_expires_at")).isNull();
  assertThat(row.get("confirmed_at")).isNull();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class)).isZero();
  // Even explicitly enabling the outbox must not send or purge capture-only signups.
  assertThat(context.getBeansOfType(OutboxWorker.class)).isEmpty();
 }
 @Test void preservesCapturedAddressOnLaterSignup()throws Exception{
  String email=address();signup(email);
  OffsetDateTime old=OffsetDateTime.now().minusDays(10).withNano(0);
  db.update("UPDATE subscribers SET created_at=?,updated_at=?",old,old);
  signup(email);
  assertThat(db.queryForObject("SELECT updated_at FROM subscribers",OffsetDateTime.class).toInstant()).isEqualTo(old.toInstant());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class)).isZero();
 }
 @Test void doesNotReactivateUnsubscribedAddress()throws Exception{
  String email=address();signup(email);db.update("UPDATE subscribers SET status='UNSUBSCRIBED'");signup(email);
  assertThat(db.queryForObject("SELECT status FROM subscribers",String.class)).isEqualTo("UNSUBSCRIBED");
 }
}
