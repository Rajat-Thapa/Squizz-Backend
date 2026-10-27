package com.squizz.subscription;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class EmailRateLimiter {
 private record Window(long started,int count) {}
 private final Map<String,Window> windows=new HashMap<>();
 public synchronized boolean allow(String email) {
  long now=System.currentTimeMillis();String key=SubscriptionService.hash(email.trim().toLowerCase(Locale.ROOT));
  Window w=windows.get(key);
  if(w==null||now-w.started()>3600000){if(windows.size()>=10000){clean();if(windows.size()>=10000)return false;}windows.put(key,new Window(now,1));return true;}
  if(w.count()>=3)return false;windows.put(key,new Window(w.started(),w.count()+1));return true;
 }
 @Scheduled(fixedDelay=600000) public synchronized void clean(){long now=System.currentTimeMillis();windows.entrySet().removeIf(e->now-e.getValue().started()>3600000);}
}
