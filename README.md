# lutronpi-smartthings
SmartThings Service Manager and Device Handlers for LutronPi server

LutronPi 2.x Server is derived from Nate Schwartz' LutronPro 1.x package for Node.js and SmartThings.  
Due to essential divergences, LutronPi 2.x Service Manager and Device Handlers for SmartThings (namespace: `lutronpi`) are _not backwards compatible_ with Nate's earlier work, and will work _only_ with the corresponding LutronPi 2.x Server running on a separate Node.js platform on the local network.

### FUNCTION:
The LutronPi application serves to connect Lutron lighting bridges (SmartBridge, SmartBridge Pro, RA2/Select repeater) to a local hub of the Samsung SmartThings home authomation platform. There is an 'official' Lutron-to-SmartThings integration, which unfortunately does not integrate the Lutron Pico remote button fobs into SmartThings. LutronPi _does_ connect the Pico buttons to SmartThings, so long as the Picos are paired to a Lutron SmartBridge Pro or RA2/Select repeater (not to a standard retail SmartBridge).

### FORM:
The LutronPi application comprises two elements:  
  1. A server application running under Node.js on an independent computer (e.g. a Raspberry Pi or like,
  or a desktop computer, or laptop, or potentially a NAS drive, etc.).  The server must be on the same
  local Ethernet subnet as the Lutron bridge(s) and the SmartThings hub.
  See: https://github.com/billhinkle/lutronpi-server
  2. A SmartThings "SmartApp" service manager application, along with its associated device handlers. These
  Groovy modules all run on the Samsung SmartThings platform, with functions local to the hub and in "the cloud."
  See: https://github.com/billhinkle/lutronpi-smartthings

### INSTALLATION:
  * You will need to work in the **SmartThings Groovy IDE** on the web: http://ide.smartthings.com and log in.
    See http://docs.smartthings.com/en/latest/tools-and-ide/github-integration.html for more IDE info.
  * You will need a Github account at http://www.github.com (but a special account just for this is fine).
  * Click on the `My SmartApps` tab and, if necessary, click on 'Enable Github integration' link, and follow 
    the instructions there to connect and authorize your Github account to your SmartThings IDE.
  * Again in the 'My SmartApps' tab, click on the `Settings` button (the one with the wrench/spanner)
  * In the **GitHub Repository Integration** window, click on `Add new repository` and fill in the fields:
    Owner: `billhinkle`  Name: `lutronpi-smartthings`  Branch: `master`
	and then click the `Save` button.
  * Click the `Update from Repo` button and then click the `lutronpi-smartthings` item.
  * Select all of the available `lutronpi` .groovy files listed and then click the `Execute Update` button.
  * The `lutronpi : LutronPi Service Manager` SmartApp should now appear in your SmartApps list.
  * Click on the `My Device Handlers` tab and confirm that the 10 `lutronpi:` device handlers appear in that list.
  * Turning now to your SmartThings phone/tablet app, select the `Automation` icon (a little house with checkmark)
  * Select the `SmartApps` tab and then `+ Add a SmartApp`.
  * Scroll to the bottom of that screen and select `My Apps`
  * Find the **Lutron Pi Service Manager** and select it.
  * The Service Manager will begin its configuration, through several successive screens.
  * Subsequently, the **LutronPi Service Manager** will appear in your SmartApps list, and can be selected there.

### CONFIGURATION:
  (to be written... in the meantime, just follow the config screens to select your LutronPi Server, set options,
  select Lutron switches/dimmers and shades and Picos, then Lutron scenes. Those selected will then appear as
  SmartThings devices on your home screen; select them for further configuration.  Picos in particular have
  numerous options set in their configuration screens (from the 'gear' icon in the upper right of their tile).

### UPDATES:
Beyond the original LutronPro 1.x package and its support of Lutron dimmers, switches and 3BRL Picos:  
  * All updates listed in the lutronpi-server README, including support of multiple Lutron bridges
  * Service Manager SmartApp: only a single LutronPi server can be selected (with multiple bridges connected to that)
  * Service Manager SmartApp: new options to enable SmartThings notifications upon bridge updates (e.g. new devices),
  bridges going offline, or the LutronPi server going offline; optionally prefix Lutron room to Lutron device name;
  optionally lock SmartThings device renaming from reset by Lutron bridges names; set Pico push/hold timing.
   * Service Manager SmartApp: more robust maintenance and update of communications with the LutronPi server
   * Service Manager SmartApp: added support for Lutron shades (only partially tested, feedback solicited!)
   * Service Manager SmartApp: added support for de-selection of 'phantom' devices that have been deleted at Lutron bridge
   * Service Manager SmartApp: added detection and notification of changes in bridge device/scene selection, and status
   * lutronpi-bridge-server device handler: display IP:port in normal format, indicate online/offline status and list connected Lutron (or other) bridges and their offline status
   * lutron-scene device handler: Lutron scenes can now be triggered by a button on the main SmartThings screen
   * lutron-scene device handler: Lutron scenes can now be triggered by a separate SmartApp
   * lutron-scene device handler: native Lutron scene information displayed for reference
   * lutron-Pico-* device handlers: added specific device handlers for all (most?) Pico types
   * lutron-Pico-* device handlers: new app button layout, responsive button icons, and new graphics for each Pico type
   * lutron-Pico-* device handlers: per-Pico configuration options for 6-second timeout, thru/to-Lutron, favorite (main screen) button, and per-button mode: press/hold, press/hold-to-repeat, or press/release
   * lutron-Pico-* device handlers: option to send app button through/to Lutron bridge so that the SmartThings virtual Pico buttons can trigger associated Lutron events as well as SmartThings events (this option may slightly slow down response of SmartThings events to app virtual Pico buttons due to the round trip through the LutronPi server and Lutron bridge)
   * lutron-Pico-* device handlers: a 'favorite' button can be configured to appear on the main SmartThings screen
   * lutron-Pico-* device handlers: native Lutron device information displayed for reference
   * lutron-dimmer device handler: new tile button layout with responsive icons, including raise/lower level buttons (Pro only), 100% level shortcut button (lower left), configurable fade enable and timing for both on and off (Pro only)
   * lutron-dimmer device handler: level refresh from Lutron at startup
   * lutron-dimmer device handler: native Lutron device information displayed for reference
   * lutron-switch device handler: new tile button layout with responsive icons
   * lutron-switch device handler: level refresh from Lutron at startup
   * lutron-switch device handler: native Lutron device information displayed for reference
   * lutron-shade device handler: all new function for Lutron shades
   * lutron-shade device handler: new graphics and tile button layout with responsive icons
   * lutron-switch device handler: native Lutron device information displayed for reference
   

   
   
