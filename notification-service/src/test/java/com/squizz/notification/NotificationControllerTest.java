package com.squizz.notification;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class NotificationControllerTest {
 String key="test-secret-with-at-least-thirty-two-characters";
 @Test void unauthorizedCallCannotSendMail(){var mail=mock(JavaMailSender.class);var controller=new NotificationController(mail,key,"hello@squizz.test");assertThat(controller.send("wrong",new NotificationController.Email("test@example.com","Confirm","Body")).getStatusCode().value()).isEqualTo(401);verifyNoInteractions(mail);}
 @Test void authorizedCallSendsToSmtp(){var mail=mock(JavaMailSender.class);var controller=new NotificationController(mail,key,"hello@squizz.test");assertThat(controller.send(key,new NotificationController.Email("test@example.com","Confirm","Body")).getStatusCode().value()).isEqualTo(204);verify(mail).send(any(SimpleMailMessage.class));}
 @Test void smtpFailureIsRetryable(){var mail=mock(JavaMailSender.class);doThrow(new MailSendException("temporary")).when(mail).send(any(SimpleMailMessage.class));var controller=new NotificationController(mail,key,"hello@squizz.test");assertThat(controller.send(key,new NotificationController.Email("test@example.com","Confirm","Body")).getStatusCode().value()).isEqualTo(503);}
}
