package it.gov.pagopa.gpd.upload.model.pd;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PaymentOptionModel implements Serializable {

    /**
     * generated serialVersionUID
     */
    private static final long serialVersionUID = -8328320637402363721L;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String nav;
    @NotBlank(message = "iuv is required")
    private String iuv;
    @NotNull(message = "amount is required")
    private Long amount;
    private String description;
    @NotNull(message = "is partial payment is required")
    private Boolean isPartialPayment;
    @NotNull(message = "due date is required")
    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime dueDate;
    private LocalDateTime retentionDate;
    private long fee;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long notificationFee;

    @Valid
    private List<TransferModel> transfer = new ArrayList<>();

    @Valid
    @Size(min=0, max=10)
    private List<PaymentOptionMetadataModel> paymentOptionMetadata = new ArrayList<>();

    public void addTransfers(TransferModel trans) {
        transfer.add(trans);
    }

    public void removeTransfers(TransferModel trans) {
        transfer.remove(trans);
    }

    public void addPaymentOptionMetadata(PaymentOptionMetadataModel paymentOptMetadata) {
        paymentOptionMetadata.add(paymentOptMetadata);
    }

    public void removePaymentOptionMetadata(PaymentOptionMetadataModel paymentOptMetadata) {
        paymentOptionMetadata.remove(paymentOptMetadata);
    }
}
