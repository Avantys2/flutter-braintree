import 'dart:io';

import 'package:flutter/services.dart';

import 'request.dart';
import 'result.dart';

class Braintree {
  static const MethodChannel _kChannel =
      const MethodChannel('flutter_braintree.custom');

  const Braintree._();

  /// Tokenizes a credit card.
  ///
  /// [authorization] must be either a valid client token or a valid tokenization key.
  /// [request] should contain all the credit card information necessary for tokenization.
  ///
  /// Returns a [Future] that resolves to a [BraintreePaymentMethodNonce] if the tokenization was successful.
  static Future<BraintreePaymentMethodNonce?> tokenizeCreditCard(
    String authorization,
    BraintreeCreditCardRequest request,
  ) async {
    final result = await _kChannel.invokeMethod('tokenizeCreditCard', {
      'authorization': authorization,
      'request': request.toJson(),
    });
    if (result == null) return null;
    return BraintreePaymentMethodNonce.fromJson(result);
  }

  /// Requests a PayPal payment method nonce.
  ///
  /// [authorization] must be either a valid client token or a valid tokenization key.
  /// [request] should contain all the information necessary for the PayPal request.
  ///
  /// Returns a [Future] that resolves to a [BraintreePaymentMethodNonce] if the user confirmed the request,
  /// or `null` if the user canceled the Vault or Checkout flow.
  static Future<BraintreePaymentMethodNonce?> requestPaypalNonce(
    String authorization,
    BraintreePayPalRequest request,
  ) async {
    final result = await _kChannel.invokeMethod('requestPaypalNonce', {
      'authorization': authorization,
      'request': request.toJson(),
    });
    if (result == null) return null;
    return BraintreePaymentMethodNonce.fromJson(result);
  }

  static Future<BraintreePaymentMethodNonce?> requestApplePayNonce(
    String authorization,
    BraintreeApplePayRequest request,
  ) async {
    final result = await _kChannel.invokeMethod('requestApplePayNonce', {
      'authorization': authorization,
      'request': request.toJson(),
    });
    if (result == null) return null;
    return BraintreePaymentMethodNonce.fromJson(result['paymentMethodNonce']);
  }

  static Future<bool> userCanPay(
    String authorization,
  ) async {
    final result = await _kChannel.invokeMethod('userCanPay', {
      'authorization': authorization,
    });
    if (result == null) return false;
    return result['isUserCanPay'];
  }

  static Future<BraintreePaymentMethodNonce?> requestPlatformPayNonce(
    String authorization,
    String amount,
    String merchant,
    String currencyCode,
    String countryCode,
  ) async {
    if (Platform.isAndroid) {
      final result = await Braintree.requestGooglePayNonce(
        authorization,
        BraintreeGooglePaymentRequest(
          totalPrice: amount,
          currencyCode: currencyCode,
          googleMerchantID: merchant,
        ),
      );
      return result;
    } else if (Platform.isIOS) {
      final item = ApplePaySummaryItem(
        amount: double.parse(amount),
        label: 'Total price',
        type: ApplePaySummaryItemType.final_,
      );

      final paymentSummaryItems = <ApplePaySummaryItem>[item];
      final supportedNetworks = [
        ApplePaySupportedNetworks.visa,
        ApplePaySupportedNetworks.masterCard,
        ApplePaySupportedNetworks.amex,
        ApplePaySupportedNetworks.discover,
      ];

      const displayName = '';

      final result = await Braintree.requestApplePayNonce(
        authorization,
        BraintreeApplePayRequest(
          paymentSummaryItems: paymentSummaryItems,
          displayName: displayName,
          currencyCode: currencyCode,
          countryCode: countryCode,
          merchantIdentifier: merchant,
          supportedNetworks: supportedNetworks,
        ),
      );

      return result;
    }
    return null;
  }

  static Future<BraintreePaymentMethodNonce?> requestGooglePayNonce(
    String authorization,
    BraintreeGooglePaymentRequest request,
  ) async {
    final result = await _kChannel.invokeMethod('requestGooglePayNonce', {
      'authorization': authorization,
      'request': request.toJson(),
    });
    if (result == null) return null;
    return BraintreePaymentMethodNonce.fromJson(result);
  }

  static Future<BraintreePaymentMethodNonce?> requestCardNonce(
    String authorization,
    BraintreeTokenizedCardRequest request,
  ) async {
    final result =
        await _kChannel.invokeMethod('requestThreeDSecureCardNonce', {
      'authorization': authorization,
      'request': request.toJson(),
    });
    if (result == null) return null;
    return BraintreePaymentMethodNonce.fromJson(result);
  }
}
