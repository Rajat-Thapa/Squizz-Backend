package com.squizz.subscription;
import java.time.*;
import java.util.*;
import java.net.http.HttpClient;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
@Component @ConditionalOnProperty(name={"app.outbox-enabled","app.confirmation-enabled"},havingValue="true",matchIfMissing=false)
public class OutboxWorker {
 private static final Logger log=LoggerFactory.getLogger(OutboxWorker.class);
 private final JdbcTemplate db;private final TransactionTemplate tx;private final RestClient client;private final String key;
 public OutboxWorker(JdbcTemplate db,TransactionTemplate tx,@Value("${app.notification-url}")String url,@Value("${app.internal-api-key}")String key){
  this.db=db;this.tx=tx;this.key=key;
  if(key.length()<32)throw new IllegalArgumentException("INTERNAL_API_KEY must have at least 32 characters");
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(20));
  this.client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 @Scheduled(fixedDelayString="${app.outbox-delay-ms}") public void dispatch(){
  for(int i=0;i<10;i++){Boolean found=tx.execute(status->{
   var rows=db.queryForList("SELECT * FROM email_outbox WHERE next_attempt_at<=? ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",OffsetDateTime.now(ZoneOffset.UTC));if(rows.isEmpty())return false;var row=rows.get(0);UUID id=(UUID)row.get("id");
   try {client.post().uri("/internal/emails").header("X-Internal-Key",key).body(Map.of("recipient",row.get("recipient"),"subject",row.get("subject"),"body",row.get("body"))).retrieve().toBodilessEntity();db.update("DELETE FROM email_outbox WHERE id=?",id);}
   catch(Exception e){int attempts=((Number)row.get("attempts")).intValue()+1;long seconds=Math.min(3600,30L*(1L<<Math.min(attempts,7)));db.update("UPDATE email_outbox SET attempts=?,next_attempt_at=? WHERE id=?",attempts,OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(seconds),id);log.warn("Email delivery deferred for outbox {} (attempt {}).",id,attempts);}
   return true;
  });if(!Boolean.TRUE.equals(found))break;}
 }
 @Scheduled(fixedDelay=3600000) public void purgeExpiredPending(){
  // Expire abandoned confirmation flows, retaining addresses captured without email.
  db.update("DELETE FROM subscribers WHERE status='PENDING' AND confirmation_hash IS NOT NULL AND updated_at<?",OffsetDateTime.now(ZoneOffset.UTC).minusDays(7));
 }
}
