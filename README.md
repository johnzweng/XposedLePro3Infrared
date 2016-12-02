# Xposed Module "LePro 3 Infrared Fix"


### What's this:

This is a module for the [Xposed Framework](http://repo.xposed.info/). You need to have the Xposed framework (which is not made by me) installed on your phone. You may need to have root to be able to install the Xposed framework (but no root is needed for this Xposed module).



### What this module can do for you:

**Note**: This module is made for the **LeEco LePro 3** phone (LEX720 and LEX727) running EUI.

The LePro 3 contains an infrared (IR) blaster but it can only be used with the preinstalled remote control app from LeEco. Other 3rd party apps from the Play Store don't work. This Xposed module tries to **make the IR blaster** of the LePro3 **usable for all 3rd party Infrared apps**.



### Short Background

The Le Pro 3 does not support the default Android [Infrared API](https://developer.android.com/reference/android/hardware/ConsumerIrManager.html) as it doesn't just contain a simple IR emitter LED, but instead contains a complex system-on-a-chip Infrared solution from Universal Electronics, which supports not only sending, but also receiving infrared ("*learning mode*").

Instead of the Android Infrared API this device uses the QuickSet SDK (from Universal Electronics) which is running as a Service (`com.uei.control.Service`) which is provided by the package `com.uei.quicksetsdk.letv` (*/system/app/UEIQuicksetSDKLeTV/UEIQuicksetSDKLeTV.apk*).

This module here overrides the methods in Androids `ConsumerIrService` class, tries to bind the UEI Control Service (*UEI QuickSet Service*) and tries to forward all calls which are received over the standard Android [ConsumerIrManager API](https://developer.android.com/reference/android/hardware/ConsumerIrManager.html) to the QuickSet SDK API. So this module tries to act as a bridge between these two APIs.

If you are interested in the technical background [read this thread on XDA](http://forum.xda-developers.com/le-pro3/development/ref-how-infrared-lepro3-infos-ir-devs-t3506257) where I explain it in detail.


### Restrictions:
- It **supports only sending Infrared** (simply because the standard Android API has no support yet for receiving infrared). But the original LeEco remote control app should still be able to use the learning-mode.
- The **QuickSet SDK Service must be installed** and must not be disabled (on the LePro 3 you can find this app under the name "*UEI Android Services SDK(LeTV)*". Its package name is: `com.uei.quicksetsdk.letv `. This is the reason why this module will probably not work on cyanogenmod. **Btw, you can remove the original LeEco remote control app if you want to.** It's not needed by this module.



### Supported EUI Version:

This module was developed and tested on the following device:

- Device **LeEco LePro 3 (LEX720)**
- Firmware version: **5.8.018S**
- Build-ID: **WAXCNFN5801811012S**

It may or may not work on other EUI versions, so just give it a try and look into the logcat logs and search for lines containig the tag: `LePro3_Infrared_Fix`.

**Please leave your feedback in the XDA thread below if you find it working for other versions or ROMs!** Thanks! :-)


### Support:
If you have any questions or feedback please visit [this thread on the XDA developers forum](http://forum.xda-developers.com/le-pro3/development/mod-make-infrared-blaster-3rd-party-t3511572) 
