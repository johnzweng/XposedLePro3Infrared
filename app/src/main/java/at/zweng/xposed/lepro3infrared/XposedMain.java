package at.zweng.xposed.lepro3infrared;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import at.zweng.xposed.lepro3infrared.quicksetservice.IControl;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodExact;

public class XposedMain implements IXposedHookLoadPackage {

    //     * Analyzing class: com.android.server.ConsumerIrService:
//     * <p>
//     * 12-01 20:53:20.215 I/Xposed  ( 3545): Xposed_IR_LePro3: let's iterate ALL methods in here...
//     * 12-01 20:53:20.215 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## public int[] com.android.server.ConsumerIrService.getCarrierFrequencies()
//     * 12-01 20:53:20.215 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## public boolean com.android.server.ConsumerIrService.hasIrEmitter()
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## public void com.android.server.ConsumerIrService.transmit(java.lang.String,int,int[])
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private static native int[] com.android.server.ConsumerIrService.halGetCarrierFrequencies(long)
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private static native long com.android.server.ConsumerIrService.halOpen()
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private static native int com.android.server.ConsumerIrService.halTransmit(long,int,int[])
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private void com.android.server.ConsumerIrService.throwIfNoIrEmitter()
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3: ..........................
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3: ..........................
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3: ..........................
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3: ..........................
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3: let's iterate ALL fields in here...
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private final android.content.Context com.android.server.ConsumerIrService.mContext
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private final java.lang.Object com.android.server.ConsumerIrService.mHalLock
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private final long com.android.server.ConsumerIrService.mNativeHal
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private final android.os.PowerManager$WakeLock com.android.server.ConsumerIrService.mWakeLock
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private static final int com.android.server.ConsumerIrService.MAX_XMIT_TIME
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3:  ## private static final java.lang.String com.android.server.ConsumerIrService.TAG
//     * 12-01 20:53:20.216 I/Xposed  ( 3545): Xposed_IR_LePro3: ..........................

    private final static String TARGET_PACKAGE = "android";
    private final static String TAG = "Xposed_IR_QuickSet";
    private final static String LOG_PREFIX = "Xposed_IR_QuickSet: ";
    /**
     * client API for the service:
     */
    private static IControl sControl;
    /**
     * Store reference to context
     */
    private static Context sContext;
    /**
     * if the quickset service is bound
     */
    private static boolean sBound = false;
    /**
     * Service Connection used to control the bound QuickSet SDK Service
     */
    private static ServiceConnection mControlServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sBound = true;
            sControl = new IControl(service);
            Log.i(TAG, "QuickSet SDK Service SUCCESSFULLY CONNECTED! Yeahh! :-)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sBound = false;
            sControl = null;
            Log.i(TAG, "QuickSet SDK Service DISCONNECTED!");
        }
    };
    /**
     * Method hook for halTransmit method:
     */
    protected static final XC_MethodHook transmitHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // private static native int com.android.server.ConsumerIrService.halTransmit(long,int,int[]);
            int carrierFrequency = (int) param.args[1];
            int[] pattern = (int[]) param.args[2];
            log(LOG_PREFIX + "halTransmit called. carrierFreq: " + carrierFrequency + ", pattern.lenth: " + pattern.length);
            int errorCode = testIrTransmit(carrierFrequency, pattern);
            // return our value to prevent real method to be called
//            param.setResult(errorCode);
        }
    };
    /**
     * Method hook for halOpen method:
     */
    protected static final XC_MethodHook halOpenHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // private static native long com.android.server.ConsumerIrService.halOpen();
            log(LOG_PREFIX + "halOpen called, returning dummy value 1 (to prevent real method to be called)");
            bindQuickSetService();
//            long result = 1;
//            param.setResult(result);
        }
    };
    /**
     * Method hook for constructor:
     */
    protected static final XC_MethodHook constructorHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            Log.i(TAG, "Constructor hook called! Try to store context parameter.");
            sContext = (Context) param.args[0];
            Log.i(TAG, "--> context obj: " + sContext);
        }
    };

//    /**
//     * Retrieve the mContext field from ConsumerIrService class
//     *
//     * @param param
//     * @return
//     */
//    private static Context getContext(MethodHookParam param) {
//        if (param.thisObject == null) {
//            Log.e(TAG, "getContext: param.thisObject is null!!");
//            return null;
//        }
//        try {
//            Field mContexField = findField(param.thisObject.getClass(), "mContext");
//            Object mContextObj = mContexField.get(param.thisObject);
//            if (mContextObj == null) {
//                Log.e(TAG, "Field mContext in class: " + param.thisObject.getClass().getSimpleName() + " was null!");
//                return null;
//            }
//            if (!(mContextObj instanceof Context)) {
//                Log.e(TAG, "Field mContext in class: " + param.thisObject.getClass().getSimpleName() + " was not of type Context: " + mContextObj.getClass().getSimpleName());
//                return null;
//            } else {
//                Log.i(TAG, "Found context :) - " + mContextObj);
//                // everything ok, return context
//                return (Context) mContextObj;
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Couldn't find field mContext in class: " + param.thisObject.getClass().getSimpleName());
//            return null;
//        }
//    }

    /**
     * Try to bind QuickSet SDK Service
     */
    private static void bindQuickSetService() {
        Log.i(TAG, "Trying to bind QuickSet control service: " + IControl.QUICKSET_UEI_PACKAGE_NAME + " - " + IControl.QUICKSET_UEI_SERVICE_CLASS);
        // TODO for debug
        checkSysFile();
        if (sContext == null) {
            Log.i(TAG, "Cannot bind QuickSet control service, as context is null. :-(");
            return;
        }
        try {
            Intent controlIntent = new Intent(IControl.ACTION);
            controlIntent.setClassName(IControl.QUICKSET_UEI_PACKAGE_NAME, IControl.QUICKSET_UEI_SERVICE_CLASS);
            boolean bindResult = sContext.bindService(controlIntent, mControlServiceConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "Control service bind result = " + bindResult);
        } catch (Exception e) {
            Log.e(TAG, "Binding QuickSet Control service failed with exception", e);
        }
    }

    /**
     * Try to send Infrared pattern, catch and log exceptions.
     *
     * @param carrierFrequency
     * @param pattern
     */
    private static int testIrTransmit(int carrierFrequency, int[] pattern) {
        Log.w(TAG, "testIrTransmit called: freq: " + carrierFrequency + ", pattern-len: " + pattern.length);
        if (sControl == null || !sBound) {
            Log.w(TAG, "QuickSet Service seems not to be bound. Trying to bind again and exit.");
            bindQuickSetService();
            return 999;
        }
        try {
            Log.i(TAG, "Calling transmit() now...");
            sControl.transmit(carrierFrequency, pattern);
            int resultcode = sControl.getLastResultcode();
            Log.i(TAG, "resultCode: " + resultcode);
            return resultcode;
        } catch (RemoteException e) {
            Log.e(TAG, "Catched Remote Exception while trying to send command to QuickSet Service.", e);
            return 999;
        }
    }

    private static void checkSysFile() {
        try {
            File enableFile = new File("/sys/remote/enable");
            Log.i(TAG, "enableFile getCanonicalPath(): " + enableFile.getCanonicalPath());
            Log.i(TAG, "enableFile exists(): " + enableFile.exists());
            Log.i(TAG, "enableFile isFile(): " + enableFile.isFile());
            Log.i(TAG, "enableFile canRead(): " + enableFile.canRead());
            Log.i(TAG, "enableFile canWrite(): " + enableFile.canWrite());
        } catch (Exception e) {
            Log.w(TAG, "check sysfile failed:", e);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            // ignore loading of all other packages
            return;
        }
        log(LOG_PREFIX + "XXXX DEBUG: We are in '" + lpparam.packageName + "' application.");
        hookConsumerIrService(lpparam);
        log(LOG_PREFIX + "XXXX DEBUG ----- END of handleLoadPackage :-) --------");
    }

    /**
     * Try to hook ConsumerIrManager
     *
     * @param lpparam
     * @throws Throwable
     */
    private void hookConsumerIrService(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //
        // 1) locate target class
        //
        Class<?> consumerIrServiceClass;
        try {
            consumerIrServiceClass = findClass("com.android.server.ConsumerIrService",
                    lpparam.classLoader);
            analyzeMethodsInClass(lpparam, consumerIrServiceClass);
        } catch (Throwable e) {
            log(LOG_PREFIX + "Could not find matching class 'com.android.server.ConsumerIrService' for hooking. Sorry, I cannot do anything. :-( :-(");
            // abort if class not found..
            return;
        }

        //
        // 2) Hook methods
        //
        try {
            // native long com.android.server.ConsumerIrService.halOpen()
            Method halOpenMethod = findMethodExact(consumerIrServiceClass, "halOpen");
            log(LOG_PREFIX + "found " + halOpenMethod.toGenericString() + " method to hook.");
            XposedBridge.hookMethod(halOpenMethod, halOpenHook);
            log(LOG_PREFIX + "successfully hooked method: " + halOpenMethod.toGenericString());
        } catch (Throwable e) {
            log(LOG_PREFIX + "Sorry, halOpen method was not found. :-(");
        }

        try {
            // native int halTransmit(long halObject, int carrierFrequency, int[] pattern);
            Method halTransmitMethod = findMethodExact(consumerIrServiceClass, "halTransmit", long.class, int.class, int[].class);
            log(LOG_PREFIX + "found " + halTransmitMethod.toGenericString() + " method to hook.");
            XposedBridge.hookMethod(halTransmitMethod, transmitHook);
            log(LOG_PREFIX + "successfully hooked method: " + halTransmitMethod.toGenericString());
        } catch (Throwable e) {
            log(LOG_PREFIX + "Sorry, transmit method was not found. :-(");
        }


        try {
            findAndHookConstructor("com.android.server.ConsumerIrService",
                    lpparam.classLoader, Context.class, constructorHook);
            log(LOG_PREFIX + "successfully hooked constructor.");
        } catch (Throwable e) {
            log(LOG_PREFIX + "Sorry, constructor was not found. :-(");
        }
    }

    /**
     * Analyze class
     *
     * @param loadpackageparam
     * @param classToAnalyze
     */
    private void analyzeMethodsInClass(
            XC_LoadPackage.LoadPackageParam loadpackageparam, Class<?> classToAnalyze) {
        try {

            // DEBUG for finding the method to hook:
            log(LOG_PREFIX + "Analyzing class: " + classToAnalyze.getCanonicalName());

            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "let's iterate ALL methods in here...");
            //Method[] allMethods = classToAnalyze.getMethods();
            Method[] allMethods = classToAnalyze.getDeclaredMethods();
            for (Method method : allMethods) {
                log(LOG_PREFIX + " ## " + method.toGenericString());
            }
            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "let's iterate ALL fields in here...");
            Field[] allFields = classToAnalyze.getDeclaredFields();
            for (Field field : allFields) {
                log(LOG_PREFIX + " ## " + field.toGenericString());
            }
            log(LOG_PREFIX + "..........................");
            log(LOG_PREFIX + "..........................");

        }
        // if anything goes wrong, log it to both logfiles..
        catch (Exception e2) {
            log(LOG_PREFIX + "could not analyze methods. Exception: " + e2
                    + ", " + e2.getMessage());
            log(LOG_PREFIX + e2.toString());
        }
    }


}

