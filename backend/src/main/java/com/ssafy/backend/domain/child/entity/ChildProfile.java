package com.ssafy.backend.domain.child.entity;

import com.ssafy.backend.domain.user.entity.User;
import com.ssafy.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "child_profiles")
public class ChildProfile extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Column(name = "avatar_key", nullable = false, length = 50)
  private String avatarKey;

  @Column(nullable = false)
  private boolean active;

  protected ChildProfile() {}

  private ChildProfile(User user, String name, LocalDate birthDate, String avatarKey) {
    this.user = user;
    this.name = name;
    this.birthDate = birthDate;
    this.avatarKey = avatarKey;
    this.active = true;
  }

  public static ChildProfile create(User user, String name, LocalDate birthDate, String avatarKey) {
    return new ChildProfile(user, name, birthDate, avatarKey);
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public String getAvatarKey() {
    return avatarKey;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String name, LocalDate birthDate, String avatarKey) {
    if (name != null) {
      this.name = name;
    }
    if (birthDate != null) {
      this.birthDate = birthDate;
    }
    if (avatarKey != null) {
      this.avatarKey = avatarKey;
    }
  }

  public void deactivate() {
    this.active = false;
  }
}
