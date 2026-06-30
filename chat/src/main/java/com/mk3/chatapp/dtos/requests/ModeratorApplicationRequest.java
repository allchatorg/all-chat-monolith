package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorApplicationRequest {

    // Section 1: Basic Information
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Time zone is required")
    private String timeZone;

    @NotBlank(message = "Country is required")
    private String country;


    // Section 2: Availability & Commitment
    @NotBlank(message = "Hours per week is required")
    private String hoursPerWeek;

    private List<String> daysAvailable;


    // Section 3: Experience
    @NotBlank(message = "Moderation experience definition is required")
    private String hasModeratedBefore;

    private String previousPlatforms;

    private String whyInterested;


    // Section 4: Judgment & Philosophy
    @NotBlank(message = "Moderator role definition is required")
    private String moderatorRoleDefinition;

    @NotBlank(message = "Freedom balance definition is required")
    private String freedomBalance;

    @NotBlank(message = "More dangerous definition is required")
    private String moreDangerous;


    // Section 5: Handling Difficult Content
    @NotBlank(message = "Comfortable with disturbing content is required")
    private String comfortableWithDisturbingContent;

    @NotBlank(message = "Unsure classification handling is required")
    private String unsureClassificationHandling;


    // Section 6: Legal & Safety Awareness
    @NotBlank(message = "Read TOS and guidelines is required")
    private String readTosAndGuidelines;

    @NotBlank(message = "Understand no download is required")
    private String understandNoDownload;

    @NotBlank(message = "Willing to follow procedures is required")
    private String willingToFollowProcedures;


    // Section 7: Confidentiality & Conduct
    @NotBlank(message = "Agree to keep confidential is required")
    private String agreeToKeepConfidential;

    @NotBlank(message = "Agree not to use for personal is required")
    private String agreeNotToUseForPersonal;


    // Section 8: Escalation & Authority
    @NotBlank(message = "Comfortable enforcing against agree is required")
    private String comfortableEnforcingAgainstAgree;

    @NotBlank(message = "How to respond to admin overrule is required")
    private String howToRespondToAdminOverrule;


    // Section 9: Optional Admin Track
    private String interestedInAdmin;

    private String whyInterestedInAdmin;


    // Section 10: Colored username
    @NotBlank(message = "Agree to colored name is required")
    private String agreeToColoredName;


    // Section 11: Final Acknowledgment
    @NotNull(message = "Confirm volunteer is required")
    private Boolean confirmVolunteer;

    @NotNull(message = "Confirm review is required")
    private Boolean confirmReview;

    @NotNull(message = "Confirm removal is required")
    private Boolean confirmRemoval;

    @NotNull(message = "Confirm unpaid is required")
    private Boolean confirmUnpaid;

    @NotBlank(message = "Signature is required")
    private String signature;

    @NotBlank(message = "Date is required")
    private String date;
}
