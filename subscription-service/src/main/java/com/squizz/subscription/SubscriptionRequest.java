package com.squizz.subscription;
import jakarta.validation.constraints.*;
import java.util.Set;
public record SubscriptionRequest(@NotBlank @Email @Size(max=254) String email,
 @NotEmpty @Size(max=3) Set<Product> interests, @AssertTrue boolean consent,
 @Size(max=300) String website) {
 public enum Product { HYDRA, GLOW, VOLT }
}
