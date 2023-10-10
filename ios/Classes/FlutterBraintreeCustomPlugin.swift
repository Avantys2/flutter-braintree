import Flutter
import UIKit
import Braintree
import BraintreeDropIn
import PassKit
import os

public class FlutterBraintreeCustomPlugin: BaseFlutterBraintreePlugin, FlutterPlugin, BTViewControllerPresentingDelegate, BTThreeDSecureRequestDelegate {
    
    private var completionBlock: FlutterResult!
    private var client: BTAPIClient?
    private var applePayInfo = [String : Any]()
    private var authorization: String!
    
    public func onLookupComplete(_ request: BTThreeDSecureRequest, lookupResult result: BTThreeDSecureResult, next: @escaping () -> Void) {
        next();
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_braintree.custom", binaryMessenger: registrar.messenger())
        
        let instance = FlutterBraintreeCustomPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        completionBlock = result
        
        guard !isHandlingResult else {
            returnAlreadyOpenError(result: result)
            return
        }
        
        isHandlingResult = true
        
        guard let authorization = getAuthorization(call: call) else {
            returnAuthorizationMissingError(result: result)
            isHandlingResult = false
            return
        }
        
        self.authorization = authorization
        
        self.client = BTAPIClient(authorization: authorization)
        
        if call.method == "requestPaypalNonce" {
            let driver = BTPayPalDriver(apiClient: client!)
            
            guard let requestInfo = dict(for: "request", in: call) else {
                isHandlingResult = false
                return
            }
            
            if let amount = requestInfo["amount"] as? String {
                let paypalRequest = BTPayPalCheckoutRequest(amount: amount)
                paypalRequest.currencyCode = requestInfo["currencyCode"] as? String
                paypalRequest.displayName = requestInfo["displayName"] as? String
                paypalRequest.billingAgreementDescription = requestInfo["billingAgreementDescription"] as? String
                if let intent = requestInfo["payPalPaymentIntent"] as? String {
                    switch intent {
                    case "order":
                        paypalRequest.intent = BTPayPalRequestIntent.order
                    case "sale":
                        paypalRequest.intent = BTPayPalRequestIntent.sale
                    default:
                        paypalRequest.intent = BTPayPalRequestIntent.authorize
                    }
                }
                if let userAction = requestInfo["payPalPaymentUserAction"] as? String {
                    switch userAction {
                    case "commit":
                        paypalRequest.userAction = BTPayPalRequestUserAction.commit
                    default:
                        paypalRequest.userAction = BTPayPalRequestUserAction.default
                    }
                }
                driver.tokenizePayPalAccount(with: paypalRequest) { (nonce, error) in
                    self.handleResult(nonce: nonce, error: error, flutterResult: result)
                    self.isHandlingResult = false
                }
            } else {
                let paypalRequest = BTPayPalVaultRequest()
                paypalRequest.displayName = requestInfo["displayName"] as? String
                paypalRequest.billingAgreementDescription = requestInfo["billingAgreementDescription"] as? String
                
                driver.tokenizePayPalAccount(with: paypalRequest) { (nonce, error) in
                    self.handleResult(nonce: nonce, error: error, flutterResult: result)
                    self.isHandlingResult = false
                }
            }
            
        } else if call.method == "tokenizeCreditCard" {
            setupTokenizedCard(call, result: result)
        } else if call.method == "requestApplePayNonce" {
            os_log("Braintree:Handle:requestApplePayNonce", type: .debug)
            guard let applePayInfo = dict(for: "request", in: call) else {return}
            self.applePayInfo = applePayInfo
            setupApplePay(flutterResult: result)
        } else if call.method == "requestThreeDSecureCardNonce" {
            os_log("Braintree:Handle:requestCardNonce", type: .debug)
            setupCardNonce(call, result: result)
        } else if call.method == "userCanPay" {
            var isAvailable = false
            if let supportedNetworksValueArray = array(for: "supportedNetworks", in: call) as? [Int] {
                let paymentNetworks = parseSupportedNetworks(supportedNetworks: supportedNetworksValueArray)
                isAvailable = PKPaymentAuthorizationViewController.canMakePayments(usingNetworks: paymentNetworks)
            }
            self.isHandlingResult = false
            result(["isUserCanPay" : isAvailable])
        } else {
            result(FlutterMethodNotImplemented)
            self.isHandlingResult = false
        }
    }
    
    private func setupTokenizedCard(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let client = self.client else { return }
        
        let cardClient = BTCardClient(apiClient: client)
        
        guard let cardRequestInfo = dict(for: "request", in: call) else {return}
        
        let card = BTCard()
        card.number = cardRequestInfo["cardNumber"] as? String
        card.expirationMonth = cardRequestInfo["expirationMonth"] as? String
        card.expirationYear = cardRequestInfo["expirationYear"] as? String
        card.cvv = cardRequestInfo["cvv"] as? String
        card.cardholderName = cardRequestInfo["cardholderName"] as? String
        
        cardClient.tokenizeCard(card) { (nonce, error) in
            self.handleResult(nonce: nonce, error: error, flutterResult: result)
            self.isHandlingResult = false
        }
    }
    
    private func setupCardNonce(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        completionBlock = result
        guard let client = self.client else { return }
        
        guard let cardInfo = dict(for: "request", in: call) else {
            os_log("Braintree:setupCardNonce: request is null", type: .error)
            return
        }
        
        let paymentFlowClient = BTPaymentFlowDriver(apiClient: client)
        paymentFlowClient.viewControllerPresentingDelegate = self
        
        let threeDSecureRequest = BTThreeDSecureRequest()
        
        if let email = cardInfo["email"] as? String {
            threeDSecureRequest.email = email
        }
        
        threeDSecureRequest.versionRequested = .version2
        
        if let token = cardInfo["token"] as? String {
            threeDSecureRequest.nonce = token
        }
        
        if let billingAddress = dict(for: "billingAddress", in: call) {
            let address = BTThreeDSecurePostalAddress()
            address.givenName = billingAddress["givenName"] as? String;
            address.surname = billingAddress["surname"] as? String;
            address.phoneNumber = billingAddress["phoneNumber"] as? String;
            address.streetAddress = billingAddress["streetAddress"] as? String;
            address.extendedAddress = billingAddress["extendedAddress"] as? String;
            address.locality = billingAddress["locality"] as? String;
            address.region = billingAddress["region"] as? String;
            address.postalCode = billingAddress["postalCode"] as? String;
            address.countryCodeAlpha2 = billingAddress["countryCodeAlpha2"] as? String;
            threeDSecureRequest.billingAddress = address
            
            // Optional additional information.
            // For best results, provide as many of these elements as possible.
            let info = BTThreeDSecureAdditionalInformation()
            info.shippingAddress = address
            threeDSecureRequest.additionalInformation = info
        }
        
        if let amount = cardInfo["amount"] as? String {
            threeDSecureRequest.threeDSecureRequestDelegate = self
            threeDSecureRequest.amount = NSDecimalNumber(string: amount)
        }
        
        var deviceData: String?
        if let collectDeviceData = bool(for: "collectDeviceData", in: call), collectDeviceData {
            deviceData = PPDataCollector.collectPayPalDeviceData()
        }
        
        paymentFlowClient.startPaymentFlow(threeDSecureRequest) { flowResult, error in
            if error != nil {
                self.handleResult(error: error, flutterResult: result)
            } else if let threeDSecureResult: BTThreeDSecureResult = flowResult as? BTThreeDSecureResult, let cardNonce = threeDSecureResult.tokenizedCard {
                
                self.handleResult(nonce: cardNonce, flutterResult: result)
            }
        }
    }
    
    private func parseSupportedNetworks(supportedNetworks supportedNetworksValueArray: [Int]) -> [PKPaymentNetwork] {
        let paymentNetworks: [PKPaymentNetwork] = supportedNetworksValueArray.compactMap({ value in
            return PKPaymentNetwork.mapRequestedNetwork(rawValue: value)
        })
        return paymentNetworks
    }
    
    private func setupApplePay(flutterResult: FlutterResult) {
        let paymentRequest = PKPaymentRequest()
        if let supportedNetworksValueArray = applePayInfo["supportedNetworks"] as? [Int] {
            let paymentNetworks = parseSupportedNetworks(supportedNetworks: supportedNetworksValueArray)
            let isAvailable = PKPaymentAuthorizationViewController.canMakePayments(usingNetworks: paymentNetworks)
            guard isAvailable else {
                self.isHandlingResult = false
                os_log("Braintree:Handle:is not available", type: .error)
                return
            }
            
            paymentRequest.supportedNetworks = paymentNetworks
        }
        
        paymentRequest.merchantCapabilities = .capability3DS
        paymentRequest.countryCode = applePayInfo["countryCode"] as! String
        paymentRequest.currencyCode = applePayInfo["currencyCode"] as! String
        paymentRequest.merchantIdentifier = applePayInfo["merchantIdentifier"] as! String
        
        guard let paymentSummaryItems = makePaymentSummaryItems(from: applePayInfo) else {
            self.isHandlingResult = false
            os_log("Braintree:Handle:paymentSummaryItems is null", type: .error)
            return;
        }
        paymentRequest.paymentSummaryItems = paymentSummaryItems;
        
        guard let applePayController = PKPaymentAuthorizationViewController(paymentRequest: paymentRequest) else {
            self.isHandlingResult = false
            os_log("Braintree:Handle:applePayController is null", type: .error)
            return
        }
        
        applePayController.delegate = self
        
        UIApplication.shared.keyWindow?.rootViewController?.present(applePayController, animated: true, completion: nil)
    }
    
    private func handleResult(nonce: BTPaymentMethodNonce? = nil, error: Error? = nil, flutterResult: FlutterResult) {
        if error != nil {
            returnBraintreeError(result: flutterResult, error: error!)
        } else if nonce == nil {
            flutterResult(nil)
        } else {
            flutterResult(buildPaymentNonceDict(nonce: nonce));
        }
    }
    
    private func handleApplePayResult(_ result: BTPaymentMethodNonce, flutterResult: FlutterResult) {
        flutterResult(["paymentMethodNonce": buildPaymentNonceDict(nonce: result)])
    }
}

// MARK: PKPaymentAuthorizationViewControllerDelegate
extension FlutterBraintreeCustomPlugin: PKPaymentAuthorizationViewControllerDelegate {
    public func paymentAuthorizationViewControllerDidFinish(_ controller: PKPaymentAuthorizationViewController) {
        isHandlingResult = false
        os_log("Braintree: Apple Pay was canceled or couldn't to pay", type: .debug)
        handleResult(nonce: nil, error: nil, flutterResult: completionBlock)
        controller.dismiss(animated: true, completion: nil)
    }
    
    @available(iOS 11.0, *)
    public func paymentAuthorizationViewController(_ controller: PKPaymentAuthorizationViewController, didAuthorizePayment payment: PKPayment, handler completion: @escaping (PKPaymentAuthorizationResult) -> Void) {
        
        self.isHandlingResult = false
        guard let apiClient = BTAPIClient(authorization: authorization) else { return }
        let applePayClient = BTApplePayClient(apiClient: apiClient)
        
        applePayClient.tokenizeApplePay(payment) { (tokenizedPaymentMethod, error) in
            guard let paymentMethod = tokenizedPaymentMethod, error == nil else {
                os_log("Braintree:%@", type: .error, String(describing: error))
                completion(PKPaymentAuthorizationResult(status: .failure, errors: nil))
                return
            }
            
            os_log("Braintree:paymentMethodNonce=%@", type: .debug, paymentMethod.nonce)
            self.handleApplePayResult(paymentMethod, flutterResult: self.completionBlock)
            completion(PKPaymentAuthorizationResult(status: .success, errors: nil))
        }
    }
    
    public func paymentAuthorizationViewController(_ controller: PKPaymentAuthorizationViewController, didAuthorizePayment payment: PKPayment, completion: @escaping (PKPaymentAuthorizationStatus) -> Void) {
        
        self.isHandlingResult = false
        guard let apiClient = BTAPIClient(authorization: authorization) else { return }
        let applePayClient = BTApplePayClient(apiClient: apiClient)
        
        applePayClient.tokenizeApplePay(payment) { (tokenizedPaymentMethod, error) in
            guard let paymentMethod = tokenizedPaymentMethod, error == nil else {
                os_log("Braintree:%@", type: .error, String(describing: error))
                completion(.failure)
                return
            }
            os_log("Braintree:paymentMethodNonce=%@", type: .debug, paymentMethod.nonce)
            self.handleApplePayResult(paymentMethod, flutterResult: self.completionBlock)
            completion(.success)
        }
    }
}

extension FlutterBraintreeCustomPlugin {
    public func paymentDriver(_ driver: Any, requestsPresentationOf viewController: UIViewController) {
        UIApplication.shared.keyWindow?.rootViewController?.present(viewController, animated: true, completion: nil)
    }

    public func paymentDriver(_ driver: Any, requestsDismissalOf viewController: UIViewController) {
        viewController.dismiss(animated: true, completion: nil)
    }
}

