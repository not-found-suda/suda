package com.ssafy.backend.domain.social.entity;

import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_accounts")
public class SocialAccount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SocialProvider provider;

  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @Column(name = "provider_email", length = 255)
  private String providerEmail;

  protected SocialAccount() {}

  private SocialAccount(
      User user, SocialProvider provider, String providerUserId, String providerEmail) {
    this.user = user;
    this.provider = provider;
    this.providerUserId = providerUserId;
    this.providerEmail = providerEmail;
  }

  public static SocialAccount create(
      User user, SocialProvider provider, String providerUserId, String providerEmail) {
    return new SocialAccount(user, provider, providerUserId, providerEmail);
  }

  public User getUser() {
    return user;
  }
}
