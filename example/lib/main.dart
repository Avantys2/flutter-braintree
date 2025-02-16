import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_braintree/flutter_braintree.dart';

void main() => runApp(
      MaterialApp(
        home: MyApp(),
      ),
    );

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static final String tokenizationKey = 'sandbox_8hxpnkht_kzdtzv2btm4p7s5j';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Braintree example app'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            ElevatedButton(
              onPressed: () async {
                var request = BraintreeDropInRequest(
                  tokenizationKey: tokenizationKey,
                  collectDeviceData: true,
                  vaultManagerEnabled: true,
                  requestThreeDSecureVerification: true,
                  email: "test@email.com",
                  billingAddress: BraintreeBillingAddress(
                    givenName: "Jill",
                    surname: "Doe",
                    phoneNumber: "5551234567",
                    streetAddress: "555 Smith St",
                    extendedAddress: "#2",
                    locality: "Chicago",
                    region: "IL",
                    postalCode: "12345",
                    countryCodeAlpha2: "US",
                  ),
                  googlePaymentRequest: BraintreeGooglePaymentRequest(
                    totalPrice: '4.20',
                    currencyCode: 'USD',
                    billingAddressRequired: false,
                  ),
                  applePayRequest: BraintreeApplePayRequest(
                      currencyCode: 'USD',
                      supportedNetworks: [
                        ApplePaySupportedNetworks.visa,
                        ApplePaySupportedNetworks.masterCard,
                        // ApplePaySupportedNetworks.amex,
                        // ApplePaySupportedNetworks.discover,
                      ],
                      countryCode: 'US',
                      merchantIdentifier: '',
                      displayName: '',
                      paymentSummaryItems: []),
                  paypalRequest: BraintreePayPalRequest(
                    amount: '4.20',
                    displayName: 'Example company',
                  ),
                  cardEnabled: true,
                );
                final result = await BraintreeDropIn.start(request);
                if (result != null) {
                  showNonce(result.paymentMethodNonce);
                }
              },
              child: Text('LAUNCH NATIVE DROP-IN'),
            ),
            ElevatedButton(
              onPressed: () async {
                final request = BraintreeCreditCardRequest(
                  cardNumber: '4111111111111111',
                  expirationMonth: '12',
                  expirationYear: '2021',
                  cvv: '123',
                );
                final result = await Braintree.tokenizeCreditCard(
                  tokenizationKey,
                  request,
                );
                if (result != null) {
                  showNonce(result);
                }
              },
              child: Text('TOKENIZE CREDIT CARD'),
            ),
            ElevatedButton(
              onPressed: () async {
                payWithSavedCard(20);
              },
              child: Text('Saved token CREDIT CARD'),
            ),
            if (Platform.isIOS)
              ElevatedButton(
                onPressed: () async {
                  payWithApplePay(80);
                },
                child: Text('Pay With Apple'),
              ),
            if (Platform.isAndroid)
              ElevatedButton(
                onPressed: () async {
                  payWithGooglePay('80');
                },
                child: Text('Pay With Google'),
              ),
            ElevatedButton(
              onPressed: () async {
                final request = BraintreePayPalRequest(
                  amount: null,
                  billingAgreementDescription:
                      'I hereby agree that flutter_braintree is great.',
                  displayName: 'Your Company',
                );
                final result = await Braintree.requestPaypalNonce(
                  tokenizationKey,
                  request,
                );
                if (result != null) {
                  showNonce(result);
                }
              },
              child: Text('PAYPAL VAULT FLOW'),
            ),
            ElevatedButton(
              onPressed: () async {
                final request = BraintreePayPalRequest(amount: '13.37');
                final result = await Braintree.requestPaypalNonce(
                  tokenizationKey,
                  request,
                );
                if (result != null) {
                  showNonce(result);
                }
              },
              child: Text('PAYPAL CHECKOUT FLOW'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> payWithSavedCard(double amount) async {
    final request = BraintreeCreditCardRequest(
      cardNumber: '4111111111111111',
      expirationMonth: '12',
      expirationYear: '2021',
      cvv: '123',
    );
    final result = await Braintree.tokenizeCreditCard(
      tokenizationKey,
      request,
    );

    if (result == null) throw "result tokenized is null";

    final cardRequest = BraintreeTokenizedCardRequest(
      amount: amount.toString(),
      token: result.nonce,
    );

    final cardResult = await Braintree.requestCardNonce(
      tokenizationKey,
      cardRequest,
    );

    if (cardResult != null) {
      showNonce(cardResult);
    }
  }

  Future<void> payWithApplePay(
    double amount, {
    String countryCode = 'US', // 🚧
    String currencyCode = 'USD',
  }) async {
    final item = ApplePaySummaryItem(
      amount: amount,
      label: 'Total price',
      type: ApplePaySummaryItemType.final_,
    );

    final merchantIdentifier = 'merchant.com.company.you_key_id';
    final paymentSummaryItems = <ApplePaySummaryItem>[item];
    final supportedNetworks = [
      ApplePaySupportedNetworks.visa,
      ApplePaySupportedNetworks.masterCard,
      ApplePaySupportedNetworks.amex,
      ApplePaySupportedNetworks.discover,
    ];

    const displayName = '';

    final result = await Braintree.requestApplePayNonce(
      tokenizationKey,
      BraintreeApplePayRequest(
        paymentSummaryItems: paymentSummaryItems,
        displayName: displayName,
        currencyCode: currencyCode,
        countryCode: countryCode,
        merchantIdentifier: merchantIdentifier,
        supportedNetworks: supportedNetworks,
      ),
    );

    if (result != null) {
      showNonce(result);
    }
  }

  Future<void> payWithGooglePay(
    String amount, {
    String currencyCode = 'USD',
  }) async {
    final result = await Braintree.requestGooglePayNonce(
      tokenizationKey,
      BraintreeGooglePaymentRequest(
        totalPrice: amount,
        currencyCode: currencyCode,
      ),
    );
    if (result != null) {
      showNonce(result);
    }
  }

  void showNonce(BraintreePaymentMethodNonce nonce) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: Text('Payment method nonce:'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text('Nonce: ${nonce.nonce}'),
            SizedBox(height: 16),
            Text('Type label: ${nonce.typeLabel}'),
            SizedBox(height: 16),
            Text('Description: ${nonce.description}'),
          ],
        ),
      ),
    );
  }
}
