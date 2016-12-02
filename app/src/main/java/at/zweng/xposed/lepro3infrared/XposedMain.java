package at.zweng.xposed.lepro3infrared;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

/**
 * Xposed Module "LePro 3 Infrared Fix"<br>
 * Hooks the default Infrared API (ConsumerIrService) and tries
 * to forward all API calls to the "QuickSet SDK service"
 * (=3rd party service used on LeEco phones for controlling
 * IR hardware)
 *
 * @author Johannes Zweng <john@zweng.at>
 */
public class XposedMain implements IXposedHookLoadPackage {

    private final static String SYS_FILE_ENABLE_IR_BLASTER = "/sys/remote/enable";
    private final static String CONSUMER_IR_SERVICE_HOST_PACKAGE = "android";
    private final static String ANDROID_SETTINGS_PACKAGE = "com.android.settings";
    private final static String TAG = "LePro3_Infrared_Fix";

    /**
     * Client API for the QuickSet control service:
     */
    private static IControl sControl;

    /**
     * Store reference to context
     */
    private static Context sContext;

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
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) SUCCESSFULLY CONNECTED! Yeah! :-)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sBound = false;
            sControl = null;
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) DISCONNECTED!");
        }
    };

    /**
     * Method hook for halTransmit method:
     */
    protected static final XC_MethodHook transmitHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            // Method: private static native int com.android.server.ConsumerIrService.halTransmit(long,int,int[]);
            int carrierFrequency = (int) param.args[1];
            int[] pattern = (int[]) param.args[2];
            int errorCode = transmitIrPattern(carrierFrequency, pattern);
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
            bindQuickSetService();
            long result = 1;
            param.setResult(result);
        }
    };

    /**
     * Try to bind QuickSet SDK Service
     */
    private static void bindQuickSetService() {
        Log.d(TAG, "Trying to bind QuickSet service (for controlling IR Blaster): " + IControl.QUICKSET_UEI_PACKAGE_NAME + " - " + IControl.QUICKSET_UEI_SERVICE_CLASS);
        if (sContext == null) {
            Log.w(TAG, "Cannot bind QuickSet control service (now), as context is null. :-(");
            return;
        }
        try {
            Intent controlIntent = new Intent(IControl.ACTION);
            controlIntent.setClassName(IControl.QUICKSET_UEI_PACKAGE_NAME, IControl.QUICKSET_UEI_SERVICE_CLASS);
            boolean bindResult = sContext.bindService(controlIntent, mControlServiceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "bindService() result: " + bindResult);
        } catch (Exception e) {
            Log.e(TAG, "Binding QuickSet Control service failed with exception :-(", e);
        }
    }

    /**
     * Try to send Infrared pattern, catch and log exceptions.
     *
     * @param carrierFrequency carrier frequency, see ConsumerIrManager Android API
     * @param pattern          IR pattern to send, see ConsumerIrManager Android API
     */
    private static int transmitIrPattern(int carrierFrequency, int[] pattern) {
        //Log.d(TAG, "transmitIrPattern called: freq: " + carrierFrequency + ", pattern-len: " + pattern.length);
        if (sControl == null || !sBound) {
            Log.w(TAG, "QuickSet Service (for using IR Blaster) seems not to be bound. Trying to bind again and exit.");
            bindQuickSetService();
            // return something != 0 to indicate error
            return 999;
        }
        try {
            sControl.transmit(carrierFrequency, pattern);
            int resultCode = sControl.getLastResultcode();
            if (resultCode != 0) {
                Log.w(TAG, "resultCode after calling transmit on QuickSet SDK was != 0. No idea what this means. lastResultcode: " + resultCode);
            }
            return resultCode;
        } catch (Exception e) {
            Log.e(TAG, "Exception while trying to send command to QuickSet Service. :-(", e);
            // return something != 0 to indicate error
            return 999;
        }
    }


    /**
     * Enable IR blaster in background thread. We start a (short-living) thread here, as
     * this write operation is blocking for 1500ms in the kernel.
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
                log(TAG + ": sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "' doesn't exist. Cannot enable IR Blaster. :-(");
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
            log(TAG + ": Exception when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-( \n" + e1.toString());
        }
    }

    /**
     * Called on package load time, the actual hooks are placed HERE!! :-)
     *
     * @param lpparam load package parameter
     * @throws Throwable if something goes wrong..
     */
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (ANDROID_SETTINGS_PACKAGE.equals(lpparam.packageName)) {
            //
            // Note: we are also targeting the system settings package here as it is
            // running in a SE Linux context, which is allowed to write into the /sys/remote/enable
            // file. So NO hooking is done here, we just hitchhike shortly in its SE context to
            // write into the sysfile. That's all.. ;-)
            //
            log(TAG + ": We are in '" + lpparam.packageName + "' package. Trying to enable IR Blaster hardware...");
            enableIrBlasterInBackgroundThread();
        } else if (CONSUMER_IR_SERVICE_HOST_PACKAGE.equals(lpparam.packageName)) {
            log(TAG + ": We are in '" + lpparam.packageName + "' package. Trying to hook Infrared API methods...");
            hookConsumerIrService(lpparam);
        }
    }


    /**
     * Try to hook ConsumerIrManager
     *
     * @param lpparam load package parameter
     * @throws Throwable
     */
    private void hookConsumerIrService(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //
        // 1) locate ConsumerIrService class..
        //
        Class<?> consumerIrServiceClass;
        try {
            consumerIrServiceClass = findClass("com.android.server.ConsumerIrService",
                    lpparam.classLoader);
        } catch (Throwable e) {
            log(TAG + ": Could not find matching class 'com.android.server.ConsumerIrService' for hooking. Sorry, I cannot do anything. :-( :-(");
            // abort if class not found..
            return;
        }

        //
        // 2) Hook methods
        //
        try {
            // native long com.android.server.ConsumerIrService.halOpen()
            Method halOpenMethod = findMethodExact(consumerIrServiceClass, "halOpen");
            XposedBridge.hookMethod(halOpenMethod, halOpenHook);
            log(TAG + ": Successfully hooked method: " + halOpenMethod.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, halOpen() method was not found. IR BLASTER WILL NOT WORK! :-(");
        }

        try {
            // native int halTransmit(long halObject, int carrierFrequency, int[] pattern);
            Method halTransmitMethod = findMethodExact(consumerIrServiceClass, "halTransmit", long.class, int.class, int[].class);
            XposedBridge.hookMethod(halTransmitMethod, transmitHook);
            log(TAG + ": Successfully hooked method: " + halTransmitMethod.toGenericString() + " :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, halTransmit() method was not found. IR BLASTER WILL NOT WORK! :-(");
        }


        try {
            // constructor, only needed to get the reference to the Context
            findAndHookConstructor("com.android.server.ConsumerIrService",
                    lpparam.classLoader, Context.class, constructorHook);
            log(TAG + ": Successfully hooked constructor of ConsumerIrService.class :-)");
        } catch (Throwable e) {
            log(TAG + ": Sorry, constructor of ConsumerIrService.class was not found. IR BLASTER WILL NOT WORK! :-(");
        }
    }

}

