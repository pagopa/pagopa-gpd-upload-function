package it.gov.pagopa.gpd.upload.model.pd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import it.gov.pagopa.gpd.upload.model.ModelGPD;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.DebtPositionStatus;
import it.gov.pagopa.gpd.upload.model.pd.enumeration.Type;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PaymentPosition implements Serializable {

    /**
     * generated serialVersionUID
     */
    private static final long serialVersionUID = 1509046053787358148L;


    @NotBlank(message = "iupd is required")
    private String iupd;
    @NotNull(message = "type is required")
    private Type type;
    @NotBlank(message = "fiscal code is required")
    private String fiscalCode;
    @NotBlank(message = "full name is required")
    private String fullName;
    private String streetName;
    private String civicNumber;
    private String postalCode;
    private String city;
    private String province;
    private String region;
    @Pattern(regexp="[A-Z]{2}", message="The country must be reported with two capital letters (example: IT)")
    private String country;
    @Email(message = "Please provide a valid email address")
    private String email;
    private String phone;
    @NotNull
    private Boolean switchToExpired;

    // Payment Position properties
    @NotBlank(message = "company name is required")
    private String companyName; // es. Comune di Roma
    @NotBlank(message = "office name is required")
    private String officeName; // es. Ufficio Tributi
    private LocalDateTime validityDate;
    @JsonProperty(access = Access.READ_ONLY)
    private LocalDateTime paymentDate;
    @JsonProperty(access = Access.READ_ONLY)
    private DebtPositionStatus status;

    @Valid
    @Builder.Default
    private List<PaymentOption> paymentOption = new ArrayList<>();

    public void addPaymentOptions(PaymentOption paymentOpt) {
        paymentOption.add(paymentOpt);
    }

    public void removePaymentOptions(PaymentOption paymentOpt) {
        paymentOption.remove(paymentOpt);
    }
}
