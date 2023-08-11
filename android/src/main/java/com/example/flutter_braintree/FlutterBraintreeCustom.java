package com.example.flutter_braintree;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.CardClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.CardTokenizeCallback;
import com.braintreepayments.api.GooglePayCardNonce;
import com.braintreepayments.api.PayPalAccountNonce;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalClient;
import com.braintreepayments.api.PayPalListener;
import com.braintreepayments.api.PayPalVaultRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.ThreeDSecureAdditionalInformation;
import com.braintreepayments.api.ThreeDSecureClient;
import com.braintreepayments.api.ThreeDSecureListener;
import com.braintreepayments.api.ThreeDSecurePostalAddress;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.api.ThreeDSecureResult;
import com.braintreepayments.api.UserCanceledException;
import com.braintreepayments.api.GooglePayListener;
import com.braintreepayments.api.GooglePayClient;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.util.HashMap;

public class FlutterBraintreeCustom extends AppCompatActivity implements PayPalListener, GooglePayListener, ThreeDSecureListener {
    private BraintreeClient braintreeClient;
    private PayPalClient payPalClient;
    private GooglePayClient googlePayClient;
    private ThreeDSecureClient threeDSecureClient;
    private GooglePayCardNonce googlePayNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);
        try {
            Intent intent = getIntent();
            braintreeClient = new BraintreeClient(this, intent.getStringExtra("authorization"));
            String type = intent.getStringExtra("type");
            Log.i("Braintree", "onCreate:type=" + type);
            switch (type) {
                case "tokenizeCreditCard":
                    tokenizeCreditCard();
                    break;
                case "requestPaypalNonce":
                    payPalClient = new PayPalClient(this, braintreeClient);
                    payPalClient.setListener(this);
                    requestPaypalNonce();
                    break;
                case "requestGooglePayNonce":
                    threeDSecureClient = new ThreeDSecureClient(this, braintreeClient);
                    threeDSecureClient.setListener(this);
                    googlePayClient = new GooglePayClient(this, braintreeClient);
                    googlePayClient.setListener(this);
                    requestGooglePayNonce();
                    break;
                case "userCanPay":
                    googlePayClient = new GooglePayClient(this, braintreeClient);
                    googlePayClient.setListener(this);
                    userCanPay();
                    break;
                default:
                    throw new Exception("Invalid request type: " + type);
            }
        } catch (Exception e) {
            Log.e("Braintree", "onCreate:" + e);
            onError(e);
        }
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        setIntent(newIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    protected void tokenizeCreditCard() {
        Intent intent = getIntent();
        Card card = new Card();
        card.setExpirationMonth(intent.getStringExtra("expirationMonth"));
        card.setExpirationYear(intent.getStringExtra("expirationYear"));
        card.setCvv(intent.getStringExtra("cvv"));
        card.setCardholderName(intent.getStringExtra("cardholderName"));
        card.setNumber(intent.getStringExtra("cardNumber"));


        CardClient cardClient = new CardClient(braintreeClient);
        CardTokenizeCallback callback = (cardNonce, error) -> {
            if(cardNonce != null){
                onPaymentMethodNonceCreated(cardNonce);
            }
            if(error != null){
                onError(error);
            }
        };
        cardClient.tokenize(card, callback);
    }

    protected void requestPaypalNonce() {
        Intent intent = getIntent();
        if (intent.getStringExtra("amount") == null) {
            // Vault flow
            PayPalVaultRequest vaultRequest = new PayPalVaultRequest();
            vaultRequest.setDisplayName(intent.getStringExtra("displayName"));
            vaultRequest.setBillingAgreementDescription(intent.getStringExtra("billingAgreementDescription"));
            payPalClient.tokenizePayPalAccount(this, vaultRequest);
        } else {
            // Checkout flow
            PayPalCheckoutRequest checkOutRequest = new PayPalCheckoutRequest(intent.getStringExtra("amount"));
            checkOutRequest.setCurrencyCode(intent.getStringExtra("currencyCode"));
            payPalClient.tokenizePayPalAccount(this, checkOutRequest);
        }
    }

    private ThreeDSecureRequest createTreeDSecure() {
        Intent intent = getIntent();
        ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();

        // For best results with 3ds 2.0, provide as many additional elements as possible.
        HashMap<String, String> billingAddress = intent.getParcelableExtra("billingAddress");
        if (billingAddress != null) {
            ThreeDSecurePostalAddress address = new ThreeDSecurePostalAddress();
            address.setGivenName(billingAddress.get("givenName")); // ASCII-printable characters required, else will throw a validation error
            address.setSurname(billingAddress.get("surname")); // ASCII-printable characters required, else will throw a validation error
            address.setPhoneNumber(billingAddress.get("phoneNumber"));
            address.setStreetAddress(billingAddress.get("streetAddress"));
            address.setExtendedAddress(billingAddress.get("extendedAddress"));
            address.setLocality(billingAddress.get("locality"));
            address.setRegion(billingAddress.get("region"));
            address.setPostalCode(billingAddress.get("postalCode"));
            address.setCountryCodeAlpha2(billingAddress.get("countryCodeAlpha2"));

            ThreeDSecureAdditionalInformation additionalInformation = new ThreeDSecureAdditionalInformation();
            additionalInformation.setShippingAddress(address);
            threeDSecureRequest.setBillingAddress(address);
            threeDSecureRequest.setAdditionalInformation(additionalInformation);
        }

        String totalPrice = intent.getStringExtra("totalPrice");
        threeDSecureRequest.setAmount(totalPrice);
        String email = intent.getStringExtra("email");
        if (email != null) {
            threeDSecureRequest.setEmail(email);
        }
        threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);

        return threeDSecureRequest;
    }

    protected void userCanPay() {
        googlePayClient.isReadyToPay(this, (isReadyToPay, error) -> {
            Log.i("Braintree", "isUserCanPay:" + isReadyToPay);
            Intent result = new Intent();

            result.putExtra("type", "userCanPay");
            HashMap<String, Object> paymentInfo = new HashMap<>();
            paymentInfo.put("isUserCanPay", isReadyToPay);
            result.putExtra("paymentInfo", paymentInfo);
            if (error != null) {
                onError(error);
            } else {
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }

    protected void requestGooglePayNonce() {
        Log.d("Braintree", "requestGooglePayNonce");
        Intent intent = getIntent();
        GooglePayRequest googlePayRequest = new GooglePayRequest();
        String merchantID = intent.getStringExtra("googleMerchantID");
        String totalPrice = intent.getStringExtra("totalPrice");
        String currencyCode = intent.getStringExtra("currencyCode");

        googlePayRequest.setTransactionInfo(TransactionInfo.newBuilder()
                .setTotalPrice(totalPrice)
                .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                .setCurrencyCode(currencyCode)
                .build());
        googlePayRequest.setEmailRequired(true);

        if (!merchantID.isEmpty()) {
            googlePayRequest.setGoogleMerchantName(merchantID);
        }

        googlePayClient.isReadyToPay(this, (isReadyToPay, e) -> {
            if (isReadyToPay) {
                googlePayClient.requestPayment(this, googlePayRequest);
            } else {
                Log.d("Braintree", "Google pay is not ready");
                onCancel();
            }
        });
    }

    protected void performThreeDSecureVerification(final FragmentActivity activity, PaymentMethodNonce paymentMethodNonce) {
        Log.d("Braintree", "performThreeDSecureVerification");
        final ThreeDSecureRequest threeDSecureRequest = this.createTreeDSecure();
        threeDSecureRequest.setNonce(paymentMethodNonce.getString());
        threeDSecureClient.performVerification(activity, threeDSecureRequest, (lookupResult, error) -> {
            Log.d("Braintree", "performVerification:lookupResult=" + lookupResult.toString());
            if (lookupResult != null) {
                Log.d("Braintree", "lookupResult != null : activity = " + activity);
                threeDSecureClient.continuePerformVerification(activity, threeDSecureRequest, lookupResult);
            } else {
                Log.e("Braintree", "lookupResult == null : " + error);
                onError(error);
            }
        });
    }

    @Override
    public void onThreeDSecureSuccess(@NonNull ThreeDSecureResult threeDSecureResult) {
        Log.d("Braintree", "onThreeDSecureSuccess:nonce = " + threeDSecureResult.getTokenizedCard().toString());
        onPaymentMethodNonceCreated(threeDSecureResult.getTokenizedCard());
    }

    @Override
    public void onThreeDSecureFailure(@NonNull Exception error) {
        Log.d("Braintree", "onThreeDSecureFailure:error = " + error);
        if (error instanceof UserCanceledException) {
            onCancel();
        } else {
            onError(error);
        }
    }

    interface ShouldRequestThreeDSecureVerification {
        void onResult(boolean shouldRequestThreeDSecureVerification);
    }

    void shouldRequestThreeDSecureVerification(PaymentMethodNonce paymentMethodNonce, final ShouldRequestThreeDSecureVerification callback) {
        if (paymentMethodCanPerformThreeDSecureVerification(paymentMethodNonce)) {
            braintreeClient.getConfiguration((configuration, error) -> {
                if (configuration == null) {
                    callback.onResult(false);
                    return;
                }

                Log.d("Braintree", "configuration: " + configuration.toJson());
                boolean shouldRequestThreeDSecureVerification = configuration.isThreeDSecureEnabled();
                callback.onResult(shouldRequestThreeDSecureVerification);
            });

        } else {
            callback.onResult(false);
        }
    }

    private boolean paymentMethodCanPerformThreeDSecureVerification(final PaymentMethodNonce paymentMethodNonce) {
        if (paymentMethodNonce instanceof CardNonce) {
            return true;
        }

        if (paymentMethodNonce instanceof GooglePayCardNonce) {
            return !((GooglePayCardNonce) paymentMethodNonce).isNetworkTokenized();
        }

        return false;
    }

    @Override
    public void onGooglePaySuccess(@NonNull PaymentMethodNonce paymentMethodNonce) {
        Log.i("Braintree", "onGooglePaySuccess:" + paymentMethodNonce);
        this.googlePayNonce = (GooglePayCardNonce) paymentMethodNonce;
        shouldRequestThreeDSecureVerification(paymentMethodNonce, shouldRequestThreeDSecureVerification -> {
            if (shouldRequestThreeDSecureVerification) {
                performThreeDSecureVerification(this, paymentMethodNonce);
            } else {
                onPaymentMethodNonceCreated(paymentMethodNonce);
            }
        });
    }

    @Override
    public void onGooglePayFailure(@NonNull Exception error) {
        Log.e("Braintree", "onGooglePayFailure:" + error);
        if (error instanceof UserCanceledException) {
            if(((UserCanceledException) error).isExplicitCancelation()){
                onCancel();
            }
        } else {
            onError(error);
        }
    }

    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        Log.d("Braintree", "onPaymentMethodNonceCreated:" + paymentMethodNonce);
        HashMap<String, Object> nonceMap = new HashMap<>();
        nonceMap.put("nonce", paymentMethodNonce.getString());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
            nonceMap.put("paypalPayerId", paypalAccountNonce.getPayerId());
            nonceMap.put("typeLabel", "PayPal");
            nonceMap.put("description", paypalAccountNonce.getEmail());
        } else if(this.googlePayNonce != null) {
            nonceMap.put("typeLabel", "GooglePay");
            nonceMap.put("description", this.googlePayNonce.getEmail());
            this.googlePayNonce = null;
        } else if(paymentMethodNonce instanceof CardNonce){
            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
            nonceMap.put("typeLabel", cardNonce.getCardType());
            nonceMap.put("description", "ending in ••" + cardNonce.getLastTwo());
        }
        Intent result = new Intent();
        result.putExtra("type", "paymentMethodNonce");
        result.putExtra("paymentMethodNonce", nonceMap);
        setResult(RESULT_OK, result);
        finish();
    }

    public void onCancel() {
        Log.d("Braintree", "onCancel");
        setResult(RESULT_CANCELED);
        finish();
    }

    public void onError(Exception error) {
        Intent result = new Intent();
        result.putExtra("error", error);
        setResult(2, result);
        finish();
    }

    @Override
    public void onPayPalSuccess(@NonNull PayPalAccountNonce payPalAccountNonce) {
        onPaymentMethodNonceCreated(payPalAccountNonce);
    }

    @Override
    public void onPayPalFailure(@NonNull Exception error) {
        if (error instanceof UserCanceledException) {
            if(((UserCanceledException) error).isExplicitCancelation()){
                onCancel();
            }
        } else {
            onError(error);
        }
    }
}