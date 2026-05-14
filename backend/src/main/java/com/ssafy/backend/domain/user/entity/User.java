package com.ssafy.backend.domain.user.entity;

import com.ssafy.backend.global.entity.BaseEntity;
import com.ssafy.backend.global.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false, length = 255)
  private String password;

  @Column(length = 50)
  private String name;

  @Column(nullable = false)
  private boolean active;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(name = "tts_speaker", nullable = false, length = 50)
  private String ttsSpeaker = TtsSpeaker.MOM_WARM.getCode();

  protected User() {}

  private User(String email, String password, String name) {
    this.email = email;
    this.password = password;
    this.name = name;
    this.active = true;
    this.role = Role.USER;
    this.ttsSpeaker = TtsSpeaker.MOM_WARM.getCode();
  }

  public static User create(String email, String password, String name) {
    return new User(email, password, name);
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }

  public Role getRole() {
    return role;
  }

  public String getTtsSpeaker() {
    return ttsSpeaker;
  }

  public void updateName(String name) {
    this.name = name;
  }

  public void updateTtsSpeaker(String ttsSpeaker) {
    this.ttsSpeaker = ttsSpeaker;
  }
}
