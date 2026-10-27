package com.squizz.subscription;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test")
class OutboxIntegrationTest {
 @Autowired JdbcTemplate db;@Autowired SubscriptionService subscriptions;@Autowired PlatformTransactionManager transactions;
 HttpServer server;AtomicReference<String> received=new AtomicReference<>();AtomicReference<String> auth=new AtomicReference<>();
 @BeforeEach void setup(){db.update("DELETE FROM email_outbox");db.update("DELETE FROM subscribers");subscriptions.subscribe(new SubscriptionRequest(UUID.randomUUID()+"@example.com",Set.of(SubscriptionRequest.Product.HYDRA),true,""));}
 @AfterEach void stop(){if(server!=null)server.stop(0);}
 OutboxWorker worker(int response)throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/emails",exchange->{received.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));auth.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));exchange.sendResponseHeaders(response,-1);exchange.close();});server.start();return new OutboxWorker(db,new TransactionTemplate(transactions),"http://127.0.0.1:"+server.getAddress().getPort(),"integration-secret-at-least-32-characters");}
 @Test void deliveredMailIsRemovedFromQueue()throws Exception{worker(204).dispatch();assertThat(received.get()).contains("confirm your Squizz email").contains("/#/confirm?token=");assertThat(auth.get()).isEqualTo("integration-secret-at-least-32-characters");assertThat(db.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class)).isZero();}
 @Test void failedMailStaysQueuedForRetry()throws Exception{worker(503).dispatch();assertThat(db.queryForObject("SELECT COUNT(*) FROM email_outbox",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT attempts FROM email_outbox",Integer.class)).isEqualTo(1);}
}
