package com.example.licenta.Services;

import com.example.licenta.Exceptions.PaymentProcessingException;
import com.example.licenta.Models.StripeIntentResponse;
import com.example.licenta.Models.User;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
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
        if (transferGroup != null && !transferGroup.isEmpty()) {
            paramsBuilder.setTransferGroup(transferGroup);
        }

        Transfer transfer = Transfer.create(paramsBuilder.build());
        System.out.println("StripeService: Created Transfer ID: " + transfer.getId() + " to Connected Account ID: " + destinationConnectedAccountId);
        return transfer;
    }

    public String getOrCreateStripeCustomerId(User user, String guestEmail) throws StripeException {
        if (user != null && user.getStripeCustomerId() != null && !user.getStripeCustomerId().isEmpty()) {
            try {
                Customer.retrieve(user.getStripeCustomerId());
                return user.getStripeCustomerId();
            } catch (StripeException e) {
                if (!"resource_missing".equals(e.getCode())) {
                    throw e;
                }
            }
        }
        CustomerCreateParams.Builder customerParamsBuilder = CustomerCreateParams.builder();
        if (user != null) {
            customerParamsBuilder.setEmail(user.getEmail());
            customerParamsBuilder.setName(user.getUsername());
            customerParamsBuilder.putMetadata("internal_user_id", user.getId());
        } else if (guestEmail != null && !guestEmail.isEmpty()) {
            customerParamsBuilder.setEmail(guestEmail);
        } else {
            customerParamsBuilder.setDescription("Guest Customer " + UUID.randomUUID().toString());
        }
        Customer customer = Customer.create(customerParamsBuilder.build());
        return customer.getId();
    }

    public StripeIntentResponse createSetupIntentForCardVerification(String stripeCustomerId, Map<String, String> metadata) throws StripeException {
        SetupIntentCreateParams.Builder paramsBuilder = SetupIntentCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION) // For future off-session payments
                .setAutomaticPaymentMethods(
                        SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                );

        if (metadata != null && !metadata.isEmpty()) {
            paramsBuilder.putAllMetadata(metadata);
        }

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("si-" + UUID.randomUUID().toString())
                .build();

        SetupIntent setupIntent = SetupIntent.create(paramsBuilder.build(), requestOptions);
        System.out.println("StripeService: Created SetupIntent ID: " + setupIntent.getId() + " for card verification");

        return new StripeIntentResponse(setupIntent.getClientSecret(), setupIntent.getId(), setupIntent.getStatus());
    }

    public StripeIntentResponse createPaymentIntentWithSavedPaymentMethod(
            long amountInSmallestUnit,
            String currency,
            String stripeCustomerId,
            String savedPaymentMethodId,
            Map<String, String> metadata) throws StripeException {

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.toLowerCase())
                .setCustomer(stripeCustomerId)
                .setPaymentMethod(savedPaymentMethodId)
                .setOffSession(true) // This is an off-session payment using saved payment method
                .setConfirm(true); // Automatically confirm the payment

        if (metadata != null && !metadata.isEmpty()) {
            paramsBuilder.putAllMetadata(metadata);
        }

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("pi-off-session-" + UUID.randomUUID().toString())
                .build();

        try {
            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build(), requestOptions);
            System.out.println("StripeService: Created off-session PaymentIntent ID: " + paymentIntent.getId() +
                    ", Status: " + paymentIntent.getStatus());

            return new StripeIntentResponse(paymentIntent.getClientSecret(), paymentIntent.getId(), paymentIntent.getStatus());
        } catch (StripeException e) {
            // Handle authentication required for off-session payments
            if ("authentication_required".equals(e.getCode())) {
                System.out.println("StripeService: Authentication required for off-session payment, creating on-session PaymentIntent");

                // Create on-session PaymentIntent that requires customer authentication
                paramsBuilder.setOffSession(false).setConfirm(false);
                PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build(), requestOptions);

                return new StripeIntentResponse(paymentIntent.getClientSecret(), paymentIntent.getId(), paymentIntent.getStatus());
            }
            throw e;
        }
    }

    public List<PaymentMethod> getCustomerPaymentMethods(String stripeCustomerId) throws StripeException {
        PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(stripeCustomerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build();

        PaymentMethodCollection paymentMethods = PaymentMethod.list(params);
        return paymentMethods.getData();
    }

    public PaymentMethod getCustomerDefaultPaymentMethod(String stripeCustomerId) throws StripeException {
        Customer customer = Customer.retrieve(stripeCustomerId);

        if (customer.getInvoiceSettings() != null &&
                customer.getInvoiceSettings().getDefaultPaymentMethod() != null) {
            return PaymentMethod.retrieve(customer.getInvoiceSettings().getDefaultPaymentMethod());
        }

        // Fallback: get the most recently created payment method
        List<PaymentMethod> paymentMethods = getCustomerPaymentMethods(stripeCustomerId);
        if (!paymentMethods.isEmpty()) {
            return paymentMethods.get(0);
        }

        return null;
    }

    public StripeIntentResponse createPaymentIntent(
            long amountInSmallestUnit, String currency, String stripeCustomerId,
            String paymentMethodId, Map<String, String> metadata, boolean offSessionAttempt,
            String setupFutureUsage
    ) throws StripeException {
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.toLowerCase())
                .setCustomer(stripeCustomerId)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build());

        if (metadata != null && !metadata.isEmpty()) {
            paramsBuilder.putAllMetadata(metadata);
        }

        if (paymentMethodId != null && !paymentMethodId.isEmpty()) {
            paramsBuilder.setPaymentMethod(paymentMethodId);
            if (offSessionAttempt) {
                paramsBuilder.setOffSession(true);
            }
        }

        if (setupFutureUsage != null && !setupFutureUsage.isEmpty()) {
            try {
                paramsBuilder.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.valueOf(setupFutureUsage));
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Invalid setup_future_usage value: " + setupFutureUsage + ". Proceeding without it.");
            }
        }
        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey("pi-" + UUID.randomUUID().toString()).build();
        PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build(), requestOptions);
        return new StripeIntentResponse(paymentIntent.getClientSecret(), paymentIntent.getId(), paymentIntent.getStatus());
    }

    public StripeIntentResponse createPaymentIntent(
            long amountInSmallestUnit, String currency, String stripeCustomerId,
            String paymentMethodId, Map<String, String> metadata, boolean offSessionAttempt
    ) throws StripeException {
        return createPaymentIntent(amountInSmallestUnit, currency, stripeCustomerId, paymentMethodId, metadata, offSessionAttempt, null);
    }

    public StripeIntentResponse createSetupIntent(String stripeCustomerId, Map<String, String> metadata) throws StripeException {
        SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION) // Or ON_SESSION if preferred for initial setup via PaymentSheet
                //.addPaymentMethodType("card") // You can add more types
                .setAutomaticPaymentMethods(
                        SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                ) // Recommended for future-proofing
                .putAllMetadata(metadata)
                .build();

        SetupIntent setupIntent = SetupIntent.create(params);
        return new StripeIntentResponse(setupIntent.getClientSecret(), setupIntent.getId(), setupIntent.getStatus());
    }

    public SetupIntent retrieveSetupIntent(String setupIntentId) throws StripeException {
        return SetupIntent.retrieve(setupIntentId);
    }

    public PaymentMethod attachPaymentMethodToCustomer(String paymentMethodId, String stripeCustomerId) throws StripeException {
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        PaymentMethodAttachParams params = PaymentMethodAttachParams.builder().setCustomer(stripeCustomerId).build();
        return paymentMethod.attach(params);
    }

    public void setDefaultPaymentMethodForCustomer(String stripeCustomerId, String paymentMethodId) throws StripeException {
        CustomerUpdateParams params = CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder().setDefaultPaymentMethod(paymentMethodId).build())
                .build();
        Customer customer = Customer.retrieve(stripeCustomerId);
        customer.update(params);
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

}