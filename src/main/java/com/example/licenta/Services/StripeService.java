package com.example.licenta.Services;

import com.example.licenta.Exceptions.PaymentProcessingException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StripeService {

    private static final String DEFAULT_COUNTRY = "RO";
    private static final String DEFAULT_CURRENCY = "RON";

    public String createBankAccountTokenAndGetId(String accountHolderName, String accountNumber) throws StripeException {
        TokenCreateParams.BankAccount tokenBankAccountParams =
                TokenCreateParams.BankAccount.builder()
                        .setCountry(DEFAULT_COUNTRY)
                        .setCurrency(DEFAULT_CURRENCY)
                        .setAccountHolderName(accountHolderName)
                        .setAccountHolderType(TokenCreateParams.BankAccount.AccountHolderType.INDIVIDUAL)
                        .setAccountNumber(accountNumber)
                        .build();

        TokenCreateParams tokenParams = TokenCreateParams.builder()
                .setBankAccount(tokenBankAccountParams)
                .build();

        RequestOptions tokenRequestOptions = RequestOptions.builder()
                .setIdempotencyKey("token-for-ba-" + UUID.randomUUID().toString())
                .build();

        System.out.println("StripeService: Attempting Token.create for AccountNumber (last 4): " + (accountNumber != null && accountNumber.length() > 4 ? accountNumber.substring(accountNumber.length() - 4) : "N/A") + ", AccountHolder: " + accountHolderName);

        Token bankAccountToken = Token.create(tokenParams, tokenRequestOptions);

        if (bankAccountToken == null) {
            System.err.println("StripeService ERROR: bankAccountToken object is NULL after Token.create call.");
            throw new PaymentProcessingException("Failed to create bank account token (Stripe returned a null token object).");
        } else if (bankAccountToken.getId() == null) {
            System.err.println("StripeService ERROR: bankAccountToken ID is NULL. Full token object JSON: " + bankAccountToken.toJson());
            throw new PaymentProcessingException("Failed to create bank account token (Stripe returned a token object with a null ID).");
        } else {
            System.out.println("StripeService INFO: Successfully created BankAccount Token ID: " + bankAccountToken.getId() +
                    ", Livemode: " + bankAccountToken.getLivemode() +
                    ", Used: " + bankAccountToken.getUsed() +
                    ", Type: " + bankAccountToken.getType());
            if (bankAccountToken.getBankAccount() != null) {
                System.out.println("StripeService INFO: Token's Bank Account details - ID: " + bankAccountToken.getBankAccount().getId() +
                        ", Last4: " + bankAccountToken.getBankAccount().getLast4() +
                        ", Status: " + bankAccountToken.getBankAccount().getStatus() +
                        ", Country: " + bankAccountToken.getBankAccount().getCountry() +
                        ", Currency: " + bankAccountToken.getBankAccount().getCurrency() +
                        ", Fingerprint: " + bankAccountToken.getBankAccount().getFingerprint() +
                        ", RoutingNumber: " + bankAccountToken.getBankAccount().getRoutingNumber());
            } else {
                System.err.println("StripeService WARNING: Token's Bank Account details (bankAccountToken.getBankAccount()) are NULL. Full token object JSON: " + bankAccountToken.toJson());
            }
        }
        return bankAccountToken.getId();
    }

    public Account createCustomConnectedAccount(String email, String countryCode) throws StripeException {
        AccountCreateParams.Builder paramsBuilder = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.CUSTOM)
                .setCountry(countryCode)
                .setEmail(email);

        paramsBuilder.setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL);

        AccountCreateParams.Capabilities.Builder capabilitiesBuilder = AccountCreateParams.Capabilities.builder();
        capabilitiesBuilder.setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build());
        paramsBuilder.setCapabilities(capabilitiesBuilder.build());

        Account account = Account.create(paramsBuilder.build());
        System.out.println("StripeService: Created Custom Connected Account ID: " + account.getId());
        return account;
    }

    public Account updateConnectedAccountIndividualDetails(
            String connectedAccountId,
            String firstName,
            String lastName,
            String email,
            AccountUpdateParams.Individual.Dob dob,
            AccountUpdateParams.Individual.Address address,
            String phone,
            String idNumber,
            AccountUpdateParams.BusinessProfile businessProfile) throws StripeException {

        AccountUpdateParams.Individual.Builder individualParamsBuilder = AccountUpdateParams.Individual.builder()
                .setFirstName(firstName)
                .setLastName(lastName)
                .setEmail(email)
                .setDob(dob)
                .setAddress(address)
                .setPhone(phone);

        if (idNumber != null && !idNumber.isEmpty()) {
            individualParamsBuilder.setIdNumber(idNumber);
        }

        AccountUpdateParams.Builder paramsBuilder = AccountUpdateParams.builder()
                .setIndividual(individualParamsBuilder.build())
                .setBusinessType(AccountUpdateParams.BusinessType.INDIVIDUAL);

        // Add business profile if provided
        if (businessProfile != null) {
            paramsBuilder.setBusinessProfile(businessProfile);
        }

        AccountUpdateParams params = paramsBuilder.build();

        Account account = Account.retrieve(connectedAccountId);
        Account updatedAccount = account.update(params);
        System.out.println("StripeService: Updated Individual Details for Connected Account ID: " + updatedAccount.getId());
        return updatedAccount;
    }

    public Account acceptTosForConnectedAccount(String connectedAccountId, String ipAddress, Long acceptanceDate) throws StripeException {
        AccountUpdateParams.TosAcceptance tosAcceptanceParams = AccountUpdateParams.TosAcceptance.builder()
                .setDate(acceptanceDate)
                .setIp(ipAddress)
                .build();

        AccountUpdateParams params = AccountUpdateParams.builder()
                .setTosAcceptance(tosAcceptanceParams)
                .build();

        Account account = Account.retrieve(connectedAccountId);
        Account updatedAccount = account.update(params);
        System.out.println("StripeService: Updated ToS Acceptance for Connected Account ID: " + updatedAccount.getId());
        return updatedAccount;
    }

    public BankAccount addExternalBankAccountToConnectedAccount(
            String connectedAccountId,
            String accountHolderName,
            String accountNumber,
            boolean setDefaultForCurrency) throws StripeException {

        String bankAccountTokenId = createBankAccountTokenAndGetId(accountHolderName, accountNumber);

        ExternalAccountCollectionCreateParams params = ExternalAccountCollectionCreateParams.builder()
                .setExternalAccount(bankAccountTokenId)
                .setDefaultForCurrency(setDefaultForCurrency)
                .build();

        Account connectedAccount = Account.retrieve(connectedAccountId);
        BankAccount bankAccount = (BankAccount) connectedAccount.getExternalAccounts().create(params);
        System.out.println("StripeService: Added External Bank Account ID: " + bankAccount.getId() + " to Connected Account ID: " + connectedAccountId);
        return bankAccount;
    }

    public Account requestCapabilitiesForConnectedAccount(String connectedAccountId, List<String> capabilityNames) throws StripeException {
        AccountUpdateParams.Capabilities.Builder capabilitiesBuilder = AccountUpdateParams.Capabilities.builder();
        for (String capability : capabilityNames) {
            if ("transfers".equals(capability)) {
                capabilitiesBuilder.setTransfers(AccountUpdateParams.Capabilities.Transfers.builder().setRequested(true).build());
            }
            if ("card_payments".equals(capability)) {
                capabilitiesBuilder.setCardPayments(AccountUpdateParams.Capabilities.CardPayments.builder().setRequested(true).build());
            }
        }

        AccountUpdateParams params = AccountUpdateParams.builder()
                .setCapabilities(capabilitiesBuilder.build())
                .build();

        Account account = Account.retrieve(connectedAccountId);
        Account updatedAccount = account.update(params);
        System.out.println("StripeService: Requested Capabilities for Connected Account ID: " + updatedAccount.getId());
        return updatedAccount;
    }

    public Account updateConnectedAccountBusinessProfile(
            String connectedAccountId,
            String businessUrl,
            String businessDescription,
            String supportUrl,
            String supportEmail,
            String supportPhone) throws StripeException {

        AccountUpdateParams.BusinessProfile.Builder businessProfileBuilder =
                AccountUpdateParams.BusinessProfile.builder()
                        .setUrl(businessUrl);

        if (businessDescription != null && !businessDescription.isEmpty()) {
            businessProfileBuilder.setMcc("7523"); // Default MCC for parking services
            businessProfileBuilder.setName(businessDescription);
        }

        if (supportUrl != null && !supportUrl.isEmpty()) {
            businessProfileBuilder.setSupportUrl(supportUrl);
        }

        if (supportEmail != null && !supportEmail.isEmpty()) {
            businessProfileBuilder.setSupportEmail(supportEmail);
        }

        if (supportPhone != null && !supportPhone.isEmpty()) {
            businessProfileBuilder.setSupportPhone(supportPhone);
        }

        AccountUpdateParams params = AccountUpdateParams.builder()
                .setBusinessProfile(businessProfileBuilder.build())
                .build();

        Account account = Account.retrieve(connectedAccountId);
        Account updatedAccount = account.update(params);
        System.out.println("StripeService: Updated Business Profile for Connected Account ID: " + updatedAccount.getId());
        return updatedAccount;
    }

    public Transfer createTransferToDestination(
            long amount,
            String currency,
            String destinationConnectedAccountId,
            Map<String, String> metadata,
            String transferGroup) throws StripeException {

        TransferCreateParams.Builder paramsBuilder = TransferCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency)
                .setDestination(destinationConnectedAccountId);

        if (metadata != null && !metadata.isEmpty()) {
            paramsBuilder.putAllMetadata(metadata);
        }
        if (transferGroup != null && !transferGroup.isEmpty()){
            paramsBuilder.setTransferGroup(transferGroup);
        }

        Transfer transfer = Transfer.create(paramsBuilder.build());
        System.out.println("StripeService: Created Transfer ID: " + transfer.getId() + " to Connected Account ID: " + destinationConnectedAccountId);
        return transfer;
    }


    public Charge createPlatformCharge(long amount, String description) throws StripeException {
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", amount);
        chargeParams.put("currency", "RON");
        chargeParams.put("source", "tok_visa");
        chargeParams.put("description", description);

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("charge-" + UUID.randomUUID().toString())
                .build();

        System.out.println("StripeService: Attempting to create platform charge for amount: " + amount + " " + "RON");
        Charge charge = Charge.create(chargeParams, requestOptions);
        System.out.println("StripeService: Successfully created platform Charge ID: " + charge.getId() + ", Status: " + charge.getStatus());
        return charge;
    }
}