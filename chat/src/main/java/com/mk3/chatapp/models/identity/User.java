package com.mk3.chatapp.models.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.TimeFormat;
import com.mk3.chatapp.models.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chat_user")
public class User extends Base implements UserDetails {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "email", unique = true, nullable = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "phone_verificaiton_date")
    private Instant phoneNumberVerificationDate;

    @Column(name = "is_over_18")
    private boolean over18;

    @Column(name = "is_over_digital_consent")
    private boolean overDigitalConsent;

    @Column(name = "accepts_terms_and_privacy")
    private boolean acceptsTermsAndPrivacy;

    @Column(name = "claimed")
    private boolean claimed;

    @Column(name = "verified")
    private boolean verified;

    @Column(name = "subscribed_to_marketing_emails")
    private boolean subscribedToMarketingEmails;

    @Column(name = "applied_for_moderator")
    private boolean appliedForModerator;

    @Column(name = "banned")
    private boolean banned;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "id_verification_status")
    private IdVerificationStatus idVerificationStatus = IdVerificationStatus.NONE;

    @Column(name = "id_verification_session_id")
    private String idVerificationSessionId;

    @Column(name = "id_verification_report_case_id")
    private Long idVerificationReportCaseId;

    @Column(name = "verified_date_of_birth")
    private LocalDate verifiedDateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "total_uploaded_files_size")
    private Long totalUploadUsage = 0L;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "display_color", length = 7)
    private String displayColor;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    // --- Fields merged from the ads-portal identity (single unified account) ---

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "accepts_policies")
    private boolean acceptsPolicies;

    @Formula("(select count(a.id) from ads a where a.owner_id = id)")
    private Long purchasedAdsCount;

    @Formula("(select coalesce(sum(a.total_cost), 0) from ads a where a.owner_id = id)")
    private Double totalSpent;

    @OneToMany(mappedBy = "user")
    @JsonIgnore
    private List<Ban> bans = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserChatRoom> userChatRooms = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_blurred_content", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @OrderBy("id ASC")
    private List<Tag> blurredContentTags = new ArrayList<>();

    @Builder.Default
    @Column(name = "time_format_setting")
    private TimeFormat timeFormatSetting = TimeFormat.H24;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<UsernameHistory> usernameHistory = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_blocked_users", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "blocked_user_id"))
    private List<User> blockedUsers = new ArrayList<>();

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "editedBy", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<MessageEditHistory> editedHistories = new ArrayList<>();

    public String getApplicationUsername() {
        return username;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.getGrantedAuthorities();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public String getPassword() {
        return password;
    }
}
