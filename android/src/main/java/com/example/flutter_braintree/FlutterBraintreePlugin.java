package com.example.flutter_braintree;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

public class FlutterBraintreePlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener {
  private static final int CUSTOM_ACTIVITY_REQUEST_CODE = 0x420;

  private Activity activity;
  private Result activeResult;

  private FlutterBraintreeDropIn dropIn;

  public static void registerWith(Registrar registrar) {
    Log.d("Braintree", "registerWith");
    FlutterBraintreeDropIn.registerWith(registrar);
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_braintree.custom");
    FlutterBraintreePlugin plugin = new FlutterBraintreePlugin();
    plugin.activity = registrar.activity();
    registrar.addActivityResultListener(plugin);
    channel.setMethodCallHandler(plugin);
  }

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    Log.d("Braintree", "onAttachedToEngine");
    final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), "flutter_braintree.custom");
    channel.setMethodCallHandler(this);

    dropIn = new FlutterBraintreeDropIn();
    dropIn.onAttachedToEngine(binding);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    Log.d("Braintree", "onDetachedFromEngine");
    dropIn.onDetachedFromEngine(binding);
    dropIn = null;
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    Log.d("Braintree", "onAttachedToActivity");
    activity = binding.getActivity();
    binding.addActivityResultListener(this);
    dropIn.onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    Log.d("Braintree", "onDetachedFromActivityForConfigChanges");
    activity = null;
    dropIn.onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    Log.d("Braintree", "onReattachedToActivityForConfigChanges");
    activity = binding.getActivity();
    binding.addActivityResultListener(this);
    dropIn.onReattachedToActivityForConfigChanges(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    Log.d("Braintree", "onDetachedFromActivity");
    activity = null;
    dropIn.onDetachedFromActivity();
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    Log.d("Braintree", "onMethodCall:" + call.method);
    if (activeResult != null) {
      result.error("already_running", "Cannot launch another custom activity while one is already running.", null);
      return;
    }

    activeResult = result;

    switch (call.method) {
      case "tokenizeCreditCard": {
        Intent intent = new Intent(activity, FlutterBraintreeCustom.class);
        intent.putExtra("type", "tokenizeCreditCard");
        intent.putExtra("authorization", (String) call.argument("authorization"));
        assert (call.argument("request") instanceof Map);
        Map request = call.argument("request");
        assert request != null;
        intent.putExtra("cardNumber", (String) request.get("cardNumber"));
        intent.putExtra("expirationMonth", (String) request.get("expirationMonth"));
        intent.putExtra("expirationYear", (String) request.get("expirationYear"));
        intent.putExtra("cvv", (String) request.get("cvv"));
        intent.putExtra("cardholderName", (String) request.get("cardholderName"));
        activity.startActivityForResult(intent, CUSTOM_ACTIVITY_REQUEST_CODE);
        break;
      }
      case "requestPaypalNonce": {
        Intent intent = new Intent(activity, FlutterBraintreeCustom.class);
        intent.putExtra("type", "requestPaypalNonce");
        intent.putExtra("authorization", (String) call.argument("authorization"));
        assert (call.argument("request") instanceof Map);
        Map request = call.argument("request");
        assert request != null;
        intent.putExtra("amount", (String) request.get("amount"));
        intent.putExtra("currencyCode", (String) request.get("currencyCode"));
        intent.putExtra("displayName", (String) request.get("displayName"));
        intent.putExtra("payPalPaymentIntent", (String) request.get("payPalPaymentIntent"));
        intent.putExtra("payPalPaymentUserAction", (String) request.get("payPalPaymentUserAction"));
        intent.putExtra("billingAgreementDescription", (String) request.get("billingAgreementDescription"));
        activity.startActivityForResult(intent, CUSTOM_ACTIVITY_REQUEST_CODE);
        break;
      }
      case "requestGooglePayNonce": {
        Intent intent = new Intent(activity, FlutterBraintreeCustom.class);
        intent.putExtra("type", "requestGooglePayNonce");
        intent.putExtra("authorization", (String) call.argument("authorization"));
        assert (call.argument("request") instanceof Map);
        Map request = call.argument("request");
        assert request != null;
        intent.putExtra("totalPrice", (String) request.get("totalPrice"));
        intent.putExtra("currencyCode", (String) request.get("currencyCode"));
        intent.putExtra("billingAgreementDescription", (String) request.get("billingAgreementDescription"));
        activity.startActivityForResult(intent, CUSTOM_ACTIVITY_REQUEST_CODE);
        break;
      }
      case "userCanPay": {
        Intent intent = new Intent(activity, FlutterBraintreeCustom.class);
        intent.putExtra("type", "userCanPay");
        intent.putExtra("authorization", (String) call.argument("authorization"));
        activity.startActivityForResult(intent, CUSTOM_ACTIVITY_REQUEST_CODE);
        break;
      }
      default:
        result.notImplemented();
        activeResult = null;
        break;
    }
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d("Braintree", "onActivityResult:resultCode=" + resultCode + ":requestCode=" + requestCode);
    if (activeResult == null) {
      return false;
    }

    switch (requestCode) {
      case CUSTOM_ACTIVITY_REQUEST_CODE:
        if (resultCode == Activity.RESULT_OK) {
          String type = data.getStringExtra("type");
          if (type.equals("paymentMethodNonce")) {
            activeResult.success(data.getSerializableExtra("paymentMethodNonce"));
          } else {
            Exception error = new Exception("Invalid activity result type.");
            activeResult.error("error", error.getMessage(), null);
          }
        } else if (resultCode == Activity.RESULT_CANCELED) {
          activeResult.success(null);
        } else {
          Exception error = (Exception) data.getSerializableExtra("error");
          activeResult.error("error", error.getMessage(), null);
        }
        activeResult = null;
        return true;
      default:
        return false;
    }
  }
}
