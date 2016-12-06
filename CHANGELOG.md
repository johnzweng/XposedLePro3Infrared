## Changelog

- v1.1
	- Renamed app_name to "**LeEco Infrared Fix**" (as it seems to work not only on the LePro 3)
	- now places addditional method hooks in Androids PackageManager to simulate having the standard infrared API available (even if it isn't)
	- sucessfully tested by XDA users on:
		- LeEco Le Pro 3 X720 running EUI 5.8.018S
		- LeEco Le 2 X526 running EUI 5.9.020S
		- LeEco Le Max 2 X820 running  [Madsurfer's 21s EUI 5.9 Rom](http://forum.xda-developers.com/le-max-2/development/madsurfers-21s-eui-5-9-rom-t3497125)

- v1.0
	- Initial version
	- Hooks the native transmit method for sending Infrared patterns and tries to forward the received IR patterns to the QuickSet SDK
	- Seems only to work on LePro 3 (tested with EUI 5.8.018S on LEX720)
	- The phone must already have infrared capabilities (as it hooks the existing Android Infrared API)
	- URL to XDA: [goo.gl/kZYp9q](http://goo.gl/kZYp9q)
