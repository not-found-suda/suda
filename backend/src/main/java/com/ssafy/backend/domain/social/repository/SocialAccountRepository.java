package com.ssafy.backend.domain.social.repository;

import com.ssafy.backend.domain.social.entity.SocialAccount;
import com.ssafy.backend.domain.social.entity.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

  Optional<SocialAccount> findByProviderAndProviderUserId(
      SocialProvider provider, String providerUserId);
}
