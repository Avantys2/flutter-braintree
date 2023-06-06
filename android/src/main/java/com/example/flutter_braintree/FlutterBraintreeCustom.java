package com.example.flutter_braintree;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.CardClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.CardTokenizeCallback;
import com.braintreepayments.api.PayPalAccountNonce;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalClient;
import com.braintreepayments.api.PayPalListener;
import com.braintreepayments.api.PayPalRequest;
import com.braintreepayments.api.PayPalVaultRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.UserCanceledException;
import com.braintreepayments.api.GooglePayListener;
import com.braintreepayments.api.GooglePayClient;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.util.HashMap;

public class FlutterBraintreeCustom extends AppCompatActivity implements PayPalListener, GooglePayListener {
    private BraintreeClient braintreeClient;
    private PayPalClient payPalClient;
    private Boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);
        try {
            Intent intent = getIntent();
            braintreeClient = new BraintreeClient(this, intent.getStringExtra("authorization"));
            String type = intent.getStringExtra("type");
            System.out.print("Braintree:onCreate:type=" + type);
            if (type.equals("tokenizeCreditCard")) {
                tokenizeCreditCard();
            } else if (type.equals("requestPaypalNonce")) {
                payPalClient = new PayPalClient(this, braintreeClient);
                payPalClient.setListener(this);
                requestPaypalNonce();
            } else if (type.equals("requestGPayNonce")) {
                requestGooglePayNonce();
            } else if (type.equals("canMakePayments")) {
                canMakePayments();
            } else {
                throw new Exception("Invalid request type: " + type);
            }
        } catch (Exception e) {
            System.out.print("Braintree:onCreate:" + e.toString());
            Intent result = new Intent();
            result.putExtra("error", e);
            setResult(2, result);
            finish();
            return;
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

    protected void requestGooglePayNonce() {
        System.out.print("Braintree:requestGooglePayNonce");
        Intent intent = getIntent();
        GooglePayRequest googlePayRequest = new GooglePayRequest();

        googlePayRequest.setTransactionInfo(TransactionInfo.newBuilder()
                .setTotalPrice(intent.getStringExtra("totalPrice"))
                .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                .setCurrencyCode(intent.getStringExtra("currencyCode"))
                .build());
        googlePayRequest.setBillingAddressRequired(true);
        
        GooglePayClient googlePayClient = new GooglePayClient(this, braintreeClient);
        googlePayClient.setListener(this);
        googlePayClient.requestPayment(this, googlePayRequest);
    }

    protected void canMakePayments() {
        GooglePayClient googlePayClient = new GooglePayClient(this, braintreeClient);
        googlePayClient.isReadyToPay(this, (isReadyToPay, error) -> {
            System.out.print("Braintree:isReadyToPay:" + isReadyToPay);
            Intent result = new Intent();
            result.putExtra("isReadyToPay", isReadyToPay);
            setResult(RESULT_OK, result);
        });
    }

    @Override
    public void onGooglePaySuccess(@NonNull PaymentMethodNonce paymentMethodNonce) {
        System.out.print("Braintree:onGooglePaySuccess:" + paymentMethodNonce.toString());
        onPaymentMethodNonceCreated(paymentMethodNonce);
    }

    @Override
    public void onGooglePayFailure(@NonNull Exception error) {
        System.out.print("Braintree:onGooglePayFailure:" + error.toString());
        if (error instanceof UserCanceledException) {
            if(((UserCanceledException) error).isExplicitCancelation()){
                onCancel();
            }
        } else {
            onError(error);
        }
    }

    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        nonceMap.put("nonce", paymentMethodNonce.getString());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
            nonceMap.put("paypalPayerId", paypalAccountNonce.getPayerId());
            nonceMap.put("typeLabel", "PayPal");
            nonceMap.put("description", paypalAccountNonce.getEmail());
        }else if(paymentMethodNonce instanceof CardNonce){
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