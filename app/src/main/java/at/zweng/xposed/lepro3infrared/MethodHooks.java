package at.zweng.xposed.lepro3infrared;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;

import static at.zweng.xposed.lepro3infrared.XposedMain.CONSUMERIR_CARRIER_FREQUENCIES;
import static at.zweng.xposed.lepro3infrared.XposedMain.TAG;
import static de.robv.android.xposed.XposedBridge.log;


/**
 * Just contains all the static method hooks.
 */
public class MethodHooks {
    /**
     * encapsulate all the magic in a dedicated class..
     */
    private final static InfraredMagic INFRARED_MAGIC = new InfraredMagic();


    /**
     * Method hook for hasItEmitter method:
     */
    protected static final XC_MethodHook hasIrEmitterHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // always return "true" on hasIrEmitter():
            param.setResult(true);
        }
    };

    /**
     * Method hook for getCarrierFrequencies method:
     */
    protected static final XC_MethodHook getCarrierFrequenciesHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // Always return a fixed list of supported carrier frequencies
            // I took this list from the AOSP example consumer_ir.c implementation:
            // https://android.googlesource.com/platform/hardware/libhardware/+/android-6.0.1_r74/modules/consumerir/consumerir.c#27
            param.setResult(CONSUMERIR_CARRIER_FREQUENCIES);
        }
    };

    /**
     * Method hook for throwIfNoIrEmitter method:
     */
    protected static final XC_MethodHook throwIfNoIrEmitterHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // don't throw anything
            param.setThrowable(null);
            // method returns void, so result won't be used.
            // just calling here to prevent actual method be called
            param.setResult(new Object());
        }
    };

    /**
     * Method hook for constructor:
     */
    protected static final XC_MethodHook constructorHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Log.i(TAG, "Constructor hook called! Try to store context parameter.");
            INFRARED_MAGIC.setContext((Context) param.args[0]);
        }
    };


    /**
     * Method hook for halTransmit method:
     */
    protected static final XC_MethodHook halTransmitHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // Method: private static native int com.android.server.ConsumerIrService.halTransmit(long,int,int[]);
            int carrierFrequency = (int) param.args[1];
            int[] pattern = (int[]) param.args[2];
            int errorCode = INFRARED_MAGIC.transmitIrPattern(carrierFrequency, pattern);
            // return our value to prevent real method to be called
            param.setResult(errorCode);
        }
    };

    /**
     * Method hook for halOpen method:
     */
    protected static final XC_MethodHook halOpenHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // Method: private static native long com.android.server.ConsumerIrService.halOpen();
            Log.i(TAG, "halOpen called, returning dummy value 1 (to prevent real method to be called)");
            INFRARED_MAGIC.bindQuickSetService();
            long result = 1;
            param.setResult(result);
        }
    };


    // ************************************
    // ** PACKAGE MANAGER SERVICE HOOKS: **
    // ******************************+*****


    /**
     * Method hook for hasSystemFeature method:
     */
    protected static final XC_MethodHook hasSystemFeatureHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            String featureName = (String) param.args[0];
            // if somebody asks for Infrared feature, then let's return true:
            if (PackageManager.FEATURE_CONSUMER_IR.equals(featureName)) {
                // TODO remove logging
                log(TAG + ": hasSystemFeatureHook: Consumer IR was requested. Will return true.");
                Log.i(TAG, "hasSystemFeatureHook: Consumer IR was requested. Will return true.");
                param.setResult(true);
            }
        }
    };

    /**
     * Method hook for hasSystemFeature method:
     */
    protected static final XC_MethodHook getSystemAvailableFeaturesHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            Object resultObj = param.getResult();
            if (resultObj == null || !(resultObj instanceof FeatureInfo[])) {
                Log.w(TAG, "getSystemAvailableFeaturesHook: return value is null or wrong type: " + resultObj);
                log(TAG + ": ERROR: getSystemAvailableFeaturesHook: return value is null or wrong type: " + resultObj);
                if (resultObj != null) {
                    log(TAG + ": getSystemAvailableFeaturesHook: " + resultObj.getClass().getCanonicalName());
                    Log.w(TAG, "ERROR: getSystemAvailableFeaturesHook type: " + resultObj.getClass().getCanonicalName());
                }
                return;
            }
            FeatureInfo[] featuresArray = (FeatureInfo[]) resultObj;
            boolean containsIr = false;
            for (FeatureInfo f : featuresArray) {
                if (PackageManager.FEATURE_CONSUMER_IR.equals(f.name)) {
                    containsIr = true;
                    break;
                }
            }
            if (!containsIr) {
                log(TAG + ": getSystemAvailableFeaturesHook: return value doesn't contain consumerIR, so we will add it");
                Log.i(TAG, "getSystemAvailableFeaturesHook: return value doesn't contain consumerIR, so we will add it");
                FeatureInfo consumerIrFeature = new FeatureInfo();
                consumerIrFeature.name = PackageManager.FEATURE_CONSUMER_IR;
                consumerIrFeature.flags = 0;
                consumerIrFeature.reqGlEsVersion = 0;
                // add feature to feature list array
                FeatureInfo[] newFeaturesArray = new FeatureInfo[featuresArray.length + 1];
                System.arraycopy(featuresArray, 0, newFeaturesArray, 0, featuresArray.length);
                newFeaturesArray[newFeaturesArray.length-1] = consumerIrFeature;
                // and set new array as result
                param.setResult(newFeaturesArray);
            } else {
                // TODO remove logging
                log(TAG + ": getSystemAvailableFeaturesHook: return value already contains IR, will do nothing");
                Log.i(TAG, "getSystemAvailableFeaturesHook: return value already contains IR, will do nothing");
            }

        }
    };


}
