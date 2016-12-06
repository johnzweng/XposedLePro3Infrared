package at.zweng.xposed.lepro3infrared;


import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;

import static at.zweng.xposed.lepro3infrared.MethodHooks.constructorHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.getCarrierFrequenciesHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.getSystemAvailableFeaturesHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.halOpenHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.hasSystemFeatureHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.throwIfNoIrEmitterHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.hasIrEmitterHook;
import static at.zweng.xposed.lepro3infrared.MethodHooks.halTransmitHook;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;
import static de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed Module "LeEco Infrared Fix"<br>
 * Hooks the default Infrared API (ConsumerIrService) and tries
 * to forward all API calls to the "QuickSet SDK service"
 * (=3rd party service used on LeEco phones for controlling
 * IR hardware)
 *
 * @author Johannes Zweng <john@zweng.at>
 */
public class XposedMain implements IXposedHookLoadPackage {

    public final static String TAG = "LeEco_Infrared_Fix";

    // frequency ranges to report back over ConsumerIr API (taken from AOSP dummy HAL implementation)
    public final static int[] CONSUMERIR_CARRIER_FREQUENCIES = {30000, 30000, 33000, 33000, 36000, 36000, 38000, 38000, 40000, 40000, 56000, 56000};
    // sys-file for power-on/power-off on some LeEco devices (not present on all phones)
    private final static String SYS_FILE_ENABLE_IR_BLASTER = "/sys/remote/enable";
    // package name to hook for the services (PackageManager, ConsumerIr)
    private final static String ANDROID_CORE_SERVICES_PACKAGE = "android";
    // package name used for hitchhiking on higher SE Linux context permissions (to write the sysfile)
    private final static String ANDROID_SETTINGS_PACKAGE = "com.android.settings";

    /**
     * Enable IR blaster in background thread. We start a (short-living) thread here, as
     * this write operation is blocking for 1500ms in the kernel driver.
     */
    private static void enableIrBlasterInBackgroundThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "enableIrBlasterInBackgroundThread thread starting... " + Thread.currentThread().getName());
                writeOneToSysFile();
                Log.d(TAG, "enableIrBlasterInBackgroundThread thread stopping... " + Thread.currentThread().getName());
            }
        }).start();
    }

    /**
     * Try to enable IR emitter by writing a '1' into sys file
     * '/sys/remote/enable'
     */
    private static void writeOneToSysFile() {
        Log.d(TAG, "will try to do: 'echo 1 > " + SYS_FILE_ENABLE_IR_BLASTER + "'");
        try {
            File enableFile = new File(SYS_FILE_ENABLE_IR_BLASTER);
            if (!enableFile.exists()) {
                log(TAG + ": sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "' doesn't exist on this phone. Maybe this phone doesn't support this mechanism for IR blaster power-on?");
                return;
            }
            if (!enableFile.isFile()) {
                log(TAG + ": sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "' is not a file! Strange... ??");
                return;
            }
            if (!enableFile.canWrite()) {
                log(TAG + ": Sorry, we don't have permission to write into sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "'. Cannot enable IR Blaster. :-(");
                return;
            }
            FileWriter fileWriter = new FileWriter(enableFile);
            fileWriter.write("1");
            fileWriter.flush();
            fileWriter.close();
            log(TAG + ": Success! IR Blaster successfully enabled. Set '" + SYS_FILE_ENABLE_IR_BLASTER + "' to 1. :-)");
        } catch (IOException e1) {
            Log.e(TAG, "Exception when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-(", e1);
            log(TAG + ": Exception when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-( \n" + e1.toString() + "\n" + e1.getMessage());
        } catch (Throwable t1) {
            Log.e(TAG, "Throwable when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-(\n" + t1.toString() + "\n" + t1.getMessage());
            log(TAG + ": Throwable when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-( \n" + t1.toString() + "\n" + t1.getMessage());
        }
    }

    /**
     * Called on package load time, the actual hooks are placed HERE!! :-)
     *
     * @param lpparam load package parameter
     */
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        try {
            if (ANDROID_SETTINGS_PACKAGE.equals(lpparam.packageName)) {
                //
                // Note: we are also targeting the system settings package here as it is
                // running in a SE Linux context, which is allowed to write into the /sys/remote/enable
                // file. So NO hooking is done here, we just hitchhike shortly in its SE context to
                // write into the sysfile. That's all.. ;-)
                //
                log(TAG + ": We are in '" + lpparam.packageName + "' package. Trying to enable IR Blaster hardware...");
                enableIrBlasterInBackgroundThread();
            } else if (ANDROID_CORE_SERVICES_PACKAGE.equals(lpparam.packageName)) {
                log(TAG + ": We are in '" + lpparam.packageName + "' package. Trying to hook Infrared API methods...");
                hookConsumerIrService(lpparam);
                hookPackageManagerService(lpparam);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Exception in handleLoadPackage. :-(", t);
            log(TAG + ": Exception in handleLoadPackage: " + t.toString());
        }
    }

    /**
     * Hooks the PackageManagerService so that it always will claim that it supports the Infrared API
     * if another app is asking. To do this, we hook 2 methods: "hasSystemFeature()" and
     * "getSystemAvailableFeatures()".
     *
     * @param lpparam load package parameter
     */
    private void hookPackageManagerService(LoadPackageParam lpparam) {

        // 1) Locate class

        Class<?> packageManagerClass;
        try {
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/pm/PackageManagerService.java
            packageManagerClass = findClass("com.android.server.pm.PackageManagerService",
                    lpparam.classLoader);
            log(TAG + ": Located class " + packageManagerClass.getSimpleName() + ". Will try to hook its methods now. :)");
        } catch (Throwable e) {
            log(TAG + ": Could not find matching class 'com.android.server.pm.PackageManagerService' for hooking. Sorry, I cannot do anything. :-( :-(");
            // abort if class not found..
            return;
        }


        // 2) Hook methods

        // hasSystemFeature
        try {
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/pm/PackageManagerService.java#3151
            Method hasSystemFeature = findMethodExact(packageManagerClass, "hasSystemFeature", String.class);
            XposedBridge.hookMethod(hasSystemFeature, hasSystemFeatureHook);
            log(TAG + ": Successfully hooked method: " + hasSystemFeature.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, hasSystemFeature() method was not found. IR BLASTER MIGHT NOT WORK! :-(");
        }

        // getSystemAvailableFeatures
        try {
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/pm/PackageManagerService.java#3132
            Method getSystemAvailableFeatures = findMethodExact(packageManagerClass, "getSystemAvailableFeatures");
            XposedBridge.hookMethod(getSystemAvailableFeatures, getSystemAvailableFeaturesHook);
            log(TAG + ": Successfully hooked method: " + getSystemAvailableFeatures.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, getSystemAvailableFeatures() method was not found. IR BLASTER MIGHT NOT WORK! :-(");
        }
    }

    /**
     * Try to hook ConsumerIrManager
     *
     * @param lpparam load package parameter
     * @throws Throwable
     */
    private void hookConsumerIrService(LoadPackageParam lpparam) throws Throwable {
        //
        // 1) locate ConsumerIrService class..
        //
        Class<?> consumerIrServiceClass;
        try {
            consumerIrServiceClass = findClass("com.android.server.ConsumerIrService",
                    lpparam.classLoader);
            log(TAG + ": Located class " + consumerIrServiceClass.getSimpleName() + ". Will try to hook its methods now. :)");
        } catch (Throwable e) {
            log(TAG + ": Could not find matching class 'com.android.server.ConsumerIrService' for hooking. Sorry, I cannot do anything. :-( :-(");
            // abort if class not found..
            return;
        }

        //
        // 2) Hook methods
        //

        // constructor
        try {
            // constructor, only needed to get the reference to the Context
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/ConsumerIrService.java#41
            findAndHookConstructor("com.android.server.ConsumerIrService",
                    lpparam.classLoader, Context.class, constructorHook);
            log(TAG + ": Successfully hooked constructor of ConsumerIrService.class :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, constructor of ConsumerIrService.class was not found. IR BLASTER WILL NOT WORK! :-(");
        }

        // halOpen
        try {
            // native long com.android.server.ConsumerIrService.halOpen()
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/ConsumerIrService.java#32
            Method halOpenMethod = findMethodExact(consumerIrServiceClass, "halOpen");
            XposedBridge.hookMethod(halOpenMethod, halOpenHook);
            log(TAG + ": Successfully hooked method: " + halOpenMethod.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, halOpen() method was not found. IR BLASTER MIGHT NOT WORK! :-(");
        }

        // halTransmit
        try {
            // native int halTransmit(long halObject, int carrierFrequency, int[] pattern);
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/ConsumerIrService.java#33
            Method halTransmitMethod = findMethodExact(consumerIrServiceClass, "halTransmit", long.class, int.class, int[].class);
            XposedBridge.hookMethod(halTransmitMethod, halTransmitHook);
            log(TAG + ": Successfully hooked method: " + halTransmitMethod.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, halTransmit() method was not found. IR BLASTER WILL NOT WORK! :-(");
        }

        // hasIrEmitter
        try {
            // public boolean hasIrEmitter()
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/ConsumerIrService.java#59
            Method hasIrEmitter = findMethodExact(consumerIrServiceClass, "hasIrEmitter");
            XposedBridge.hookMethod(hasIrEmitter, hasIrEmitterHook);
            log(TAG + ": Successfully hooked method: " + hasIrEmitter.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, hasIrEmitter() method was not found. IR BLASTER MIGHT NOT WORK! :-(");
        }

        // getCarrierFrequencies
        try {
            // public int[] getCarrierFrequencies()
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/ConsumerIrService.java#103
            Method getCarrierFrequencies = findMethodExact(consumerIrServiceClass, "getCarrierFrequencies");
            XposedBridge.hookMethod(getCarrierFrequencies, getCarrierFrequenciesHook);
            log(TAG + ": Successfully hooked method: " + getCarrierFrequencies.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, getCarrierFrequencies() method was not found. IR BLASTER MIGHT NOT WORK! :-(");
        }

        // throwIfNoIrEmitter
        try {
            // private void throwIfNoIrEmitter()
            // https://android.googlesource.com/platform/frameworks/base/+/android-6.0.1_r74/services/core/java/com/android/server/ConsumerIrService.java#63
            // prevent that implementation throws exception when there is no native HAL loaded
            Method throwIfNoIrEmitter = findMethodExact(consumerIrServiceClass, "throwIfNoIrEmitter");
            XposedBridge.hookMethod(throwIfNoIrEmitter, throwIfNoIrEmitterHook);
            log(TAG + ": Successfully hooked method: " + throwIfNoIrEmitter.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, throwIfNoIrEmitter() method was not found. IR BLASTER MIGHT NOT WORK! :-(");
        }
    }


}

