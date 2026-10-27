package com.squizz.subscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SubscriptionIntegrationTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 @BeforeEach void clear(){db.update("DELETE FROM email_outbox");db.update("DELETE FROM subscribers");}
 String address(){return UUID.randomUUID()+"@example.com";}
 String request(String email,boolean consent){return "{\"email\":\""+email+"\",\"interests\":[\"HYDRA\",\"GLOW\"],\"consent\":"+consent+",\"website\":\"\"}";}
 void signup(String email)throws Exception{mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request(email,true))).andExpect(status().isAccepted());}
 String token(String kind){String body=db.queryForObject("SELECT body FROM email_outbox LIMIT 1",String.class);return body.split("/#/"+kind+"\\?token=")[1].split("\\s")[0];}
 void act(String kind,String token,int expected)throws Exception{mvc.perform(post("/api/v1/subscriptions/"+kind).contentType("application/json").content(json.writeValueAsString(Map.of("token",token)))).andExpect(status().is(expected));}
 @Test void capturesNormalizesAndDeduplicates()throws Exception{String email=address().toUpperCase(Locale.ROOT);signup(email);signup(email.toLowerCase(Locale.ROOT));assertThat(db.queryForObject("SELECT COUNT(*) FROM subscribers",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT email FROM subscribers",String.class)).isEqualTo(email.toLowerCase(Locale.ROOT));assertThat(db.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT status FROM subscribers",String.class)).isEqualTo("PENDING");}
 @Test void confirmationAndUnsubscribeAreIdempotent()throws Exception{signup(address());String confirm=token("confirm"),unsub=token("unsubscribe");act("confirm",confirm,200);act("confirm",confirm,200);assertThat(db.queryForObject("SELECT status FROM subscribers",String.class)).isEqualTo("ACTIVE");act("unsubscribe",unsub,200);act("unsubscribe",unsub,200);assertThat(db.queryForObject("SELECT status FROM subscribers",String.class)).isEqualTo("UNSUBSCRIBED");assertThat(db.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class)).isZero();act("confirm",confirm,400);}
 @Test void rejectsExpiredConfirmations()throws Exception{signup(address());String token=token("confirm");db.update("UPDATE subscribers SET confirmation_expires_at=?",OffsetDateTime.now().minusHours(1));act("confirm",token,400);}
 @Test void rejectsInvalidEmailsAndMissingConsent()throws Exception{mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request("not-an-email",true))).andExpect(status().isBadRequest());mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request(address(),false))).andExpect(status().isBadRequest());assertThat(db.queryForObject("SELECT COUNT(*) FROM subscribers",Integer.class)).isZero();}
 @Test void honeypotDoesNotPersist()throws Exception{mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request(address(),true).replace("\"website\":\"\"","\"website\":\"bot\""))).andExpect(status().isAccepted());assertThat(db.queryForObject("SELECT COUNT(*) FROM subscribers",Integer.class)).isZero();}
 @Test void limitsRepeatedRequests()throws Exception{String email=address();for(int i=0;i<3;i++)signup(email);mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request(email,true))).andExpect(status().isTooManyRequests());}
 @Test void unknownProductIsRejected()throws Exception{mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request(address(),true).replace("HYDRA","ENERGY"))).andExpect(status().isBadRequest());}
 @Test void activeSubscriptionCannotBeOverwrittenByAnonymousSignup()throws Exception{String email=address();signup(email);act("confirm",token("confirm"),200);mvc.perform(post("/api/v1/subscriptions").contentType("application/json").content(request(email,true).replace("HYDRA","VOLT"))).andExpect(status().isAccepted());assertThat(db.queryForObject("SELECT interests FROM subscribers",String.class)).isEqualTo("GLOW,HYDRA");}
}
