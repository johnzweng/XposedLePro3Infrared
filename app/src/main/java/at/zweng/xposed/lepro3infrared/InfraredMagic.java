package at.zweng.xposed.lepro3infrared;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import at.zweng.xposed.lepro3infrared.quicksetservice.IControl;

import static at.zweng.xposed.lepro3infrared.XposedMain.TAG;
import static de.robv.android.xposed.XposedBridge.log;

/**
 * Perform the actual infrared magic in here...
 */
public class InfraredMagic {

    /**
     * Client API for the QuickSet control service:
     */
    private IControl mControl;

    /**
     * Store reference context of hooked ConsumerIrService (as we need it for binding a service)
     */
    private Context mContext;

    /**
     * Flag if we have successfully bound the QuickSet service
     */
    private boolean mBound = false;

    /**
     * Service Connection used to control the bound QuickSet SDK Service
     */
    private final ServiceConnection mControlServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBound = true;
            mControl = new IControl(service);
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) SUCCESSFULLY CONNECTED! Yeah! :-)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mControl = null;
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) DISCONNECTED!");
        }
    };


    /**
     * Try to send Infrared pattern, catch and log exceptions.
     *
     * @param carrierFrequency carrier frequency, see ConsumerIrManager Android API
     * @param pattern          IR pattern to send, see ConsumerIrManager Android API
     */
    public int transmitIrPattern(int carrierFrequency, int[] pattern) {
        //Log.d(TAG, "transmitIrPattern called: freq: " + carrierFrequency + ", pattern-len: " + pattern.length);
        if (mControl == null || !mBound) {
            Log.w(TAG, "QuickSet Service (for using IR Blaster) seems not to be bound. Trying to bind again and exit.");
            bindQuickSetService();
            // return something != 0 to indicate error
            return 999;
        }
        try {
            mControl.transmit(carrierFrequency, pattern);
            int resultCode = mControl.getLastResultcode();
            if (resultCode != 0) {
                Log.w(TAG, "resultCode after calling transmit on QuickSet SDK was != 0. No idea what this means. lastResultcode: " + resultCode);
            }
            return resultCode;
        } catch (Throwable t) {
            Log.e(TAG, "Exception while trying to send command to QuickSet Service. :-(", t);
            // return something != 0 to indicate error
            return 999;
        }
    }

    /**
     * Try to bind QuickSet SDK Service
     */
    public void bindQuickSetService() {
        Log.d(TAG, "Trying to bind QuickSet service (for controlling IR Blaster): " + IControl.QUICKSET_UEI_PACKAGE_NAME + " - " + IControl.QUICKSET_UEI_SERVICE_CLASS);
        if (mContext == null) {
            Log.w(TAG, "Cannot bind QuickSet control service (now), as context is null. :-(");
            return;
        }
        try {
            Intent controlIntent = new Intent(IControl.ACTION);
            controlIntent.setClassName(IControl.QUICKSET_UEI_PACKAGE_NAME, IControl.QUICKSET_UEI_SERVICE_CLASS);
            boolean bindResult = mContext.bindService(controlIntent, mControlServiceConnection, Context.BIND_AUTO_CREATE);
            if (!bindResult) {
                Log.e(TAG, "bindResult == false. QuickSet SDK service seems NOT TO BE AVAILABLE ON THIS PHONE! IR Blaster will probably NOT WORK!");
                Log.e(TAG, "QuickSet SDK service package/class: " + IControl.QUICKSET_UEI_PACKAGE_NAME + "/" + IControl.QUICKSET_UEI_SERVICE_CLASS);
                log(TAG + ": QuickSet SDK service seems NOT TO BE AVAILABLE ON THIS PHONE! IR Blaster will probably NOT WORK!");
                log(TAG + ": QuickSet SDK service package/class: " + IControl.QUICKSET_UEI_PACKAGE_NAME + "/" + IControl.QUICKSET_UEI_SERVICE_CLASS);
            } else {
                Log.d(TAG, "bindService() result: true");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Binding QuickSet Control service failed with exception :-(", t);
        }
    }

    /**
     * Store reference to context
     *
     * @param ctx context
     */
    public void setContext(Context ctx) {
        this.mContext = ctx;
    }

}
