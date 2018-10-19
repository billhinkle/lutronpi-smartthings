/**
 *  Lutron Pi Service Manager
 *
 *		05/10/2017		Version:1.0				Initial Public Release [as Lutron Pro Service Manager] - njschwartz
 *		05/12/2017		Version:1.0-20170512	added held button capability [as Lutron Pro Service Manager] - njschwartz
 *		10/19/2017		Version:1.0-20171019	Updated to handle the new SSL Lutron server.  [as Lutron Pro Service Manager] - njschwartz
 *												Also added the ability to use standard smart bridges again and added scene control back
 *		05/11/2018		Version:2.0-20180511	All below:  [as Lutron Pi Service Manager] - wjh
 *												Modified various variable,function,method names, groovy getters, for clarity and consistency
 *												Added singleInstance: true to prevent the scourge of multiple instances of this app
 *												Fixed SSDP subscription/discovery to work continuously, not just once at startup
 *												Modified SSDP monitor to recognize & sync IP/port changes by node server & mod its device name to match
 *												Modified SSDP monitor to recognize SSDP location changes /connected -> /subscribe as node server startup
 *												Converted SSDP parse to use built-in parseLanMessage method, converted all other IP:Port to standard form, not SSDP hex
 *												Ensure LutronPi server has been seen via SSDP discovery recently before skipping that config page
 *												Added explicit subscription call to node server by SmartApp GET'ing /subscribe method & recognize its footprint
 *												Modified child device data to eliminate unused items and add some new useful items including Bridge ID (S/N)
 *												Modified DNI assignment to better support multiple Lutron bridges: bridgeID.deviceSN (or bridgeID.virtualbutton#)
 *												Modified server/switch/Pico/scenes selection pages to hide-when-empty until populated; shortened initial refresh intervals
 *												Enhanced deletion of unselected child devices to also delete child devices for devices deleted from the bridge(s)/node server
 *												Added a selection section to allow manual de-selection of devices deleted from the bridge(s)/node server (can't do it in code!)
 *												Added exception handling at addChildDevice invocations in case of missing Device Handlers
 *												Refactored child device -to- node server I/O handling for consistency and enhanced handshake to accomodate multiple Lutron bridges
 *												Added support for additional Pico configurations and associated device handlers
 *												Added support for all Pico options/modes/timing to be handled at the device or SmartApp, and sent to the node server
 *												Added support for Lutron shades & associated device handler
 *												Propagated Lutron device name or other data changes to child devices upon update from LutronPi, plus option to lock ST name revisions
 *												Added support for polling for Lutron updates via a hash from the LutronPi at /connected or /subscribe
 *												Added options for notification on Lutron updates and concatenation of Lutron room:device names
 *												Added update notification subsystem via bridge device/scene config hash from Lutron Pi polling
 *												Added support for ST app virtual Pico buttons to trigger Lutron pico functions directly, and loop back to trigger ST actions
 *												Added support for ST app Lutron scene trigger to loop back and trigger ST actions as well
 *		06/11/2018		Version:2.0-20180625	tweaked LutronPi discovery & subscription to improve handling of multiple servers - wjh
 *		07/06/2018		Version:2.0-20180706	tweaked LutronPi offline/online detection & polling to cover some edge cases; added 'online' notifications - wjh
 *		10/05/2018		Version:2.0-20181005	Added support for manually adding/removing devices for bridges that do not enumerate their own device list
 *												Corrected a bug with deletion of phantom shades and Picos
 *												Cleaned up deletion of inactive child devices
 *		10/18/2018		Version:2.0-20181018	No longer attempts to add child devices if the LutronPi Bridge Server device doesn't exist/can't be created
 *		
 *  Copyright 2017 Nate Schwartz
 *	Copyright 2018 William Hinkle (github: billhinkle) for Contributions noted wjh, which are assigned as Contributions to Licensor per Apache License Version 2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import org.json.JSONObject

definition(
		name: "LutronPi Service Manager",
		namespace: "lutronpi",
		author: "Nate Schwartz + Bill Hinkle",
		description: "Interface to talk with Lutron SmartBridge and add Pico Remotes v2+",
		category: "SmartThings Labs",
		iconUrl:	"http://i65.tinypic.com/nq8shi.png", // "http://oi65.tinypic.com/2evgrx4.jpg",
		iconX2Url:	"http://i65.tinypic.com/nq8shi.png",
		iconX3Url:	"http://i65.tinypic.com/nq8shi.png",
		singleInstance: true)


preferences {
	page(name:"mainPage", title:"Configuration", content:"mainPage")
	page(name:"lPiDiscovery", title:"LutronPi Server Discovery", content:"lPiDiscovery")
	page(name:"lPiFirstSelected", title:"LutronPi Server Selected", content:"lPiFirstSelected")
	page(name:"optionsSelect", title:"LutronPi Options", content:"optionsSelect")
	page(name:"deviceDiscovery", title:"Lutron Device Setup", content:"deviceDiscovery")
    page(name:"deviceBuilder", title:"Device Builder", content:"deviceBuilder")
    page(name:"deviceBuilderValidate", title:"Device Builder Validate", content:"deviceBuilderValidate")
    page(name:"deviceRemoverConfirm", title:"Device Remover Confirm", content:"deviceRemoverConfirm")
	page(name:"sceneDiscovery", title:"Lutron Scene Setup", content:"sceneDiscovery")
    page(name:"sceneBuilder", title:"Scene Builder", content:"sceneBuilder")
    page(name:"sceneBuilderValidate", title:"Scene Builder Validate", content:"sceneBuilderValidate")
	page(name:"sceneRemoverConfirm", title:"Scene Remover Confirm", content:"sceneRemoverConfirm")
}

// UI select flags
def getSelectorFlag() {
	if (!state.selectorFlag) {
		state.selectorFlag = [initLutronPi:true, initSwitch:true, initScene:true]
	}
	state.selectorFlag
}

def mainPage() {
//	log.debug "Made it to mainPage()"
	// Check to see if the LutronPi Server already (and still) exists; if so, skip LutronPi discovery, else load LutronPi discovery
	def allLutronPi = lutronPiMap
	if (allLutronPi?.containsKey(selectedLPi)) {
		if (lPiReDiscovery(lutronPiMap[selectedLPi]))
			return lPiFirstSelected()
	}
	if (allLutronPi.size() > 1 || !allLutronPi?.containsKey(selectedLPi)) {	// insure we get a fresh set of LutronPi discoveries
		def selLPiValue = null
		if (allLutronPi?.containsKey(selectedLPi))
			selLPiValue = allLutronPi[selectedLPi]
		allLutronPi.clear()
		if (selLPiValue)
			allLutronPi[selectedLPi] = selLPiValue
		state.ssdpSubscribed = false;
	}
    return lPiDiscovery()
}

def lPiFirstSelected() {
//	log.debug "Made it to lPiFirstSelected()"
	// subscribe to the server when the server is first selected, before setting options and discovering devices
	if (lutronPiMap.size() && selectedLPi) {
		subscribeLutronPi()
		selectorFlag.initSwitch = true;
		selectorFlag.initScene = true;
		return optionsSelect()	// continue to options setup
	}
	return lPiDiscovery()
}

Boolean lPiReDiscovery(lPiDevice) {
	def ssdpLastTimestamp = lPiDevice.timestamp ?: 0
	def msSinceLastTimestamp = now() - ssdpLastTimestamp
	log.debug "LutronPi Rediscovery ${selectedLPi} in ${lutronPiMap} (${msSinceLastTimestamp / 1000}sec)"
	return (msSinceLastTimestamp < (60000 * 5 + 5000))	// ok if we've seen the LutronPi at the last ssdp discovery
}

// Preferences page to add LutronPi devices
def lPiDiscovery() {
//	log.debug "Made it to lPiDiscovery()"
	def refreshInterval

	// Populate the preferences page with found devices
	def lutronPiSelectList = lutronPiListForDialog
	if (!state.ssdpSubscribed) {
     	refreshInterval = 2
		// Perform LutronPi server LAN search vis SSDP
		ssdpSubscribe()
		log.debug 'Performing initial LutronPi discovery'
	} else {
		refreshInterval = 10
	}
	ssdpDiscover()

	return dynamicPage(name:"lPiDiscovery", title:"LutronPi Server Discovery", nextPage:"lPiFirstSelected", refreshInterval: refreshInterval, uninstall: true) {
		section(hideWhenEmpty: false, "Select your LutronPi Server") {
			input "selectedLPi", "enum", required:true, title:"Select LutronPi Server \n(${lutronPiSelectList.size() ?: 0} found)", multiple:false,
			      options:lutronPiSelectList, image: "http://i65.tinypic.com/nq8shi.png"
		}
	}
}

// Preferences page to set various options for this SmartApp
def optionsSelect() {
//	log.debug "Made it to optionsSelect()"
	return dynamicPage(name:"optionsSelect", title:"LutronPi Options", nextPage:"deviceDiscovery", uninstall: true) {
		section() {
			input "notifyOnBridgeUpdate", "bool", title:"Notify of bridge updates", defaultValue:false, required:true
			input "lRoomDeviceNames", "bool", title:"Lutron room:switch names", defaultValue:false, required:true
			input "lockSTDeviceNames", "bool", title:"App device renames locked", defaultValue:true, required:true
		}
		section("Lutron Pico options") {
			input "picoShortPushTime", "number", title:"Pico Min Hold Time (ms)", range: "200..1500", defaultValue:300, required:false
			input "picoRepeatHoldIntervalTime", "number", title:"Pico Hold Repeat Time (ms)", range: "250..1500",defaultValue:750, required:false
		}
	}
}

// Preferences page to add Lutron devices (Dimmers/Switches/Picos/Shades)
def deviceDiscovery() {
//	log.debug "Made it to deviceDiscovery()"
	def refreshInterval = 2
	// log.debug "Selected switches=$selectedSwitches"
	// log.debug "Selected picos=$selectedPicos"
	// log.debug "Selected shades=$selectedShades"

	def bridgeDeviceEnumeration = bridgeDevicesEnumerated()
	def switchOptions = switchesDiscovered()
	def picoOptions = picosDiscovered()
	def shadeOptions = shadesDiscovered()

	// if there are devices pending removal on this page refresh, do that instead of device selection, then link back to this page
	if (devicesToRemove) {
		return deviceRemoverConfirm()
	}

	def switchPhantoms = selectedSwitches.findAll { k-> !switchOptions[k]}
	def shadePhantoms = selectedShades.findAll { k-> !shadeOptions[k]}
	def picoPhantoms = selectedPicos.findAll { k-> !picoOptions[k]}
	def devicesBuiltButUnselected = state.builtDevices.findAll { k, v -> !selectedSwitches?.contains(k) &&
	                                                                     !selectedShades?.contains(k) &&
	                                                                     !selectedPicos?.contains(k) }
	// Populate the preferences page with found devices
	discoverBridgeDevices()

	if (!selectorFlag.initSwitch) {
		refreshInterval = 10
	} else
		selectorFlag.initSwitch = false;

	log.debug "Made it to deviceDiscovery preferences"
	return dynamicPage(name:"deviceDiscovery", title:"Device Discovery", nextPage:"sceneDiscovery", refreshInterval: refreshInterval, uninstall: false) {

		section(hideWhenEmpty: true, "Select Switches & Dimmers (${(selectedSwitches?.size()?:0) - (switchPhantoms?.size()?:0)} of ${switchOptions.size()?:0} found)") {
			input "selectedSwitches", "enum", required: false, submitOnChange: true, title: "",
			      multiple: true, options: switchOptions, image: "http://i63.tinypic.com/kb3jty.png"
		}
        
		section(hideWhenEmpty: true, "Select Shades (${(selectedShades?.size()?:0) - (shadePhantoms?.size()?:0)} of ${shadeOptions.size()?:0} found)") {
			input "selectedShades", "enum", required: false, submitOnChange: true, title: "",
			      multiple: true, options: shadeOptions, image: "http://oi68.tinypic.com/2e2dk4z.jpg"
		}
 		
		section(hideWhenEmpty: true, "Select Picos & Keypads (${(selectedPicos?.size()?:0) - (picoPhantoms?.size()?:0)} of ${picoOptions.size()?:0} found)") {
			input "selectedPicos", "enum", required: false, submitOnChange: true, title: "",
			      multiple: true, options: picoOptions, image: "http://i67.tinypic.com/4hujyv.png"
			paragraph "Pico keypads on a standard  Lutron Caséta SmartBridge cannot control devices in SmartThings.",
					  hideWhenEmpty: "selectedPicos"
		}
		section(hideWhenEmpty: true) {
			if (bridgeDeviceEnumeration.any { k,v -> !v }) {	// only offer to add devices to bridges that do not enumerate/list their devices for us
            	app.updateSetting("deviceBuilderBridge", null)	// should choose between bridges with its bridgeDeviceEnumeration value false
                app.updateSetting("deviceBuilderType", null)	// ["Switch","Dimmer","Shade","Pico","Keypad"]
                app.updateSetting("deviceBuilderID", [type:"text", value: ""])	// for Switch/Dimmer and Shade this is Zone
				app.updateSetting("deviceBuilderMaxBtn", [type:"number", value: "1"])	// for Keypad only max button #
                app.updateSetting("deviceBuilderName", [type:"text", value: ''])
				app.updateSetting("deviceBuilderRoom", [type:"text", value: ''])
				href name:"deviceBuilderPage", title: "Add a Device", description: "tap to specify",
                     required: false, page: "deviceBuilder", image: "st.custom.buttons.add-icon"
			}
            if (devicesBuiltButUnselected.size()) {
				app.updateSetting("devicesToRemove", [])
				def deviceRemovalCandidates = [:]
				devicesBuiltButUnselected.each { k, v -> deviceRemovalCandidates[k] = "${v.name} (${v.dni})" }

				input "devicesToRemove", "enum", required: false, submitOnChange: true, title:"Remove Added Devices",
                      description: "from ${devicesBuiltButUnselected.size()} currently unselected",
				      multiple: true, options: deviceRemovalCandidates, image: "st.custom.buttons.subtract-icon"
            }
		}
		section(hideWhenEmpty: true, "Deleted / Please de-select:") {
			input "selectedSwitches", "enum", required: false, submitOnChange: true, title: "De-select phantom switches",
			      multiple:true, options: switchPhantoms, image: "st.custom.buttons.subtract-icon"
			input "selectedShades", "enum", required: false, submitOnChange: true, title: "De-select phantom shades",
			      multiple:true, options: shadePhantoms, image: "st.custom.buttons.subtract-icon"
			input "selectedPicos", "enum", required: false, submitOnChange: true, title: "De-select phantom Picos",
			      multiple:true, options: picoPhantoms, image: "st.custom.buttons.subtract-icon"
		}
	}
}

// Preferences page to add Lutron scenes
def sceneDiscovery() {
//	log.debug "Made it to sceneDiscovery()"
	def refreshInterval = 2
	// log.debug selectedScenes

	def bridgeDeviceEnumeration = bridgeDevicesEnumerated()
	def sceneOptions = scenesDiscovered()

	// if there are scenes pending removal on this page refresh, do that instead of scene selection, then link back to this page
	if (scenesToRemove) {
    	return sceneRemoverConfirm()
    }

	def scenePhantoms = selectedScenes.findAll { k-> !sceneOptions[k] }
    def scenesBuiltButUnselected = state.builtScenes.findAll { k, v -> !selectedScenes?.contains(k) }

	// Populate the preferences page with found scenes
	discoverBridgeScenes()

	if (!selectorFlag.initScene /* sceneOptions != [:] */) {
		refreshInterval = 10
	} else
		selectorFlag.initScene = false;

	return dynamicPage(name:"sceneDiscovery", title:"Scene Discovery", nextPage:"", refreshInterval: refreshInterval, install: true, uninstall: false) {
		section(hideWhenEmpty: true, "Select Scenes/Virtual Buttons (${(selectedScenes?.size()?:0) - (scenePhantoms?.size()?:0)} of ${sceneOptions?.size()?:0} found)") {
			input "selectedScenes", "enum", required: false, submitOnChange: true, title: "",
			      multiple: true, options: sceneOptions,image: "http://oi65.tinypic.com/znwu9i.jpg"
		}
        section(hideWhenEmpty: true) {
			if (bridgeDeviceEnumeration.any { k,v -> !v }) {	// only offer to add devices to bridges that do not enumerate/list their devices for us
            	app.updateSetting("sceneBuilderBridge", null)
                app.updateSetting("sceneBuilderID", [type:"number", value: "0"])
                app.updateSetting("sceneBuilderName", [type:"text", value: ''])
				href name:"sceneBuilderPage", title: "Add a Scene/Virtual Button", description: "tap to specify",
                     required: false, page: "sceneBuilder", image: "st.custom.buttons.add-icon"
			}
            if (scenesBuiltButUnselected.size()) {
				app.updateSetting("scenesToRemove", [])
				def sceneRemovalCandidates = [:]
				scenesBuiltButUnselected.each { k, v -> sceneRemovalCandidates[k] = "${v.name} (${v.dni})" }

				input "scenesToRemove", "enum", required:false, submitOnChange:true, title:"Remove scenes/virtual buttons",
                      description: "from ${scenesBuiltButUnselected.size()} currently unselected",
				      multiple:true, options: sceneRemovalCandidates, image: "st.custom.buttons.subtract-icon"
            }
		}
		section(hideWhenEmpty: true, "Deleted / Please de-select:") {
			input "selectedScenes", "enum", required:false, submitOnChange:true, title:"De-select phantom scenes",
			      multiple:true, options:scenePhantoms, image: "st.custom.buttons.subtract-icon"
		}
	}
}

private deviceBuilder() {
	def bridgesEligible = bridgeDevicesEnumerated().findAll({ k,v -> !v }).keySet() as List 	// eligible bridge only if it doesn't enumerate/list its devices

	return dynamicPage(name: "deviceBuilder", title: "Device Builder", nextPage: "deviceBuilderValidate", uninstall: false) {
		section {
			input "deviceBuilderBridge", "enum", required: true, submitOnChange: false,
			      title:"Bridge:",
			      multiple:false, options: bridgesEligible
			input "deviceBuilderType", "enum", required: true, submitOnChange: true,
			      title:"Device Type:",
			      multiple: false, options: ['WallDimmer','PlugInDimmer','WallSwitch','Shade','Pico2Button','Pico2ButtonRaiseLower','Pico3Button','Pico3ButtonRaiseLower','Pico4Button','Pico4ButtonScene','Pico4ButtonZone','Pico4Button2Group','Keypad']
			input "deviceBuilderID", "text", required: true, submitOnChange: true,
                  title: "Device ID/Integration ID#:",
			      defaultValue: ""
			if (deviceBuilderType == 'Keypad') {
				input "deviceBuilderMaxBtn", "number", required: true, submitOnChange: false,
				      title: "Max Buttons #:",
				      range: "1..*", defaultValue: 1
			}
			input "deviceBuilderName", "text", required: true, submitOnChange: false, title:"Device Name:"
			input "deviceBuilderRoom", "text", required: false, submitOnChange: false, title:"Device Room:"
		}
    }
}

private deviceBuilderValidate() {
	def pageProperties = [
		name: "deviceBuilderValidate",
		title: "Device Builder"
   	]
	// use deviceID as a positive integer if possible
    def lipID = deviceBuilderID.isInteger() ? (deviceBuilderID.toInteger().abs()?:-1) : -1
    def devID = (lipID > 0) ? lipID as String : deviceBuilderID
	if (deviceBuilderBridge && deviceBuilderType && (lipID > 0 || deviceBuilderID) && deviceBuilderName &&
        !(switchesDiscovered()+shadesDiscovered()+picosDiscovered()).any { k,v -> (bridgeOfDNI(k) == deviceBuilderBridge) &&
                                                                                  (serialNumberOfDNI(k) == deviceBuilderID.toString()) }
	   ) {
		pageProperties.nextPage = "deviceDiscovery"
        def newDeviceRoom = deviceBuilderRoom ?: ''
		def newDevice = createDeviceSpec(deviceBuilderBridge, deviceBuilderType,
                                         devID, (deviceBuilderType == 'Keypad') ? deviceBuilderMaxBtn : devID,
		                                 deviceBuilderName, newDeviceRoom, devID)
		state.builtDevices[newDevice.dni] = newDevice

		return dynamicPage(pageProperties) {
   			section {
   				paragraph "Added Device:\nBridge: $deviceBuilderBridge\nDevice Type: $deviceBuilderType\nDevice/Zone ID # $devID\n" +
                          "Name: $deviceBuilderName\n${newDeviceRoom?'Room: ':''}$newDeviceRoom"
				paragraph "Tap 'Done', or 'Back' to add another."
				paragraph "Select added devices on the next page to use them now."
       		}
		}
	} else {
		pageProperties.nextPage = "deviceBuilder"

   		return dynamicPage(pageProperties) {
   			section {
   				paragraph "Cannot add this Device for Bridge: $deviceBuilderBridge; # $deviceBuilderID is invalid or may already exist.\n\nTap 'Done' to correct the specification."
       		}
		}
	}
}

private deviceRemoverConfirm() {
	def pageProperties = [
		name: "deviceRemoverConfirm",
		title: "Device Removal",
		nextPage: "deviceDiscovery"
   	]
    def drCount = "No"
	if (devicesToRemove) {
		devicesToRemove.each { k -> state.builtDevices.remove(k)}
	    drCount = "${devicesToRemove.size()}"
	}
    app.updateSetting("devicesToRemove", [])
	return dynamicPage(pageProperties) {
		section {
			paragraph drCount + " device${(drCount!='1')?'s were':' was'} removed.\n\nTap 'Next'."
		}
	}
}

private sceneBuilder() {
	def bridgesEligible = bridgeDevicesEnumerated().findAll({ k,v -> !v }).keySet() as List 	// eligible bridge only if it doesn't enumerate/list its devices

	return dynamicPage(name: "sceneBuilder", title: "Scene / Virtual Button Builder", nextPage: "sceneBuilderValidate", uninstall: false) {
		section {
			input "sceneBuilderBridge", "enum", required:true, submitOnChange: false, title: "Bridge:",
			      multiple: false, options: bridgesEligible
			input "sceneBuilderID", "number", required:true, submitOnChange: false, title: "Scene/Virtual Button #",
			      multiple: false, range: "1..*", defaultValue: 1
			input "sceneBuilderName", "text", required: true, submitOnChange: false, title: "Scene/Virtual Button Name:",
			      multiple: false
		}
    }
}

private sceneBuilderValidate() {
	def pageProperties = [
		name: "sceneBuilderValidate",
		title: "Scene / Virtual Button Builder",
   	]
	if (sceneBuilderBridge && sceneBuilderID && sceneBuilderName &&
        !scenesDiscovered().any { k,v -> (bridgeOfDNI(k) == sceneBuilderBridge) &&
                                         (sceneNumberOfDNI(k) as Integer == sceneBuilderID) }
		) {
		pageProperties.nextPage = "sceneDiscovery"
		def newScene = createSceneSpec(sceneBuilderBridge, sceneBuilderID, sceneBuilderName)
		state.builtScenes[newScene.dni] = newScene

		return dynamicPage(pageProperties) {
   			section {
   				paragraph "Added Scene/Virtual Button:\nBridge: $sceneBuilderBridge\nScene/Virtual Button # $sceneBuilderID\nName: $sceneBuilderName"
				paragraph "Tap 'Done', or 'Back' to add another."
				paragraph "Select added Scenes/Virtual Buttons on the next page to use them now."
			}
		}
	} else {
		pageProperties.nextPage = "sceneBuilder"

   		return dynamicPage(pageProperties) {
   			section {
   				paragraph "Cannot add this Scene/Virtual Button for Bridge: $sceneBuilderBridge; # $sceneBuilderID may already exist.\n\nTap 'Done' to correct the specification."
       		}
		}
	}
}

private sceneRemoverConfirm() {
	def pageProperties = [
		name: "sceneRemoverConfirm",
		title: "Scene / Virtual Button Removal",
		nextPage: "sceneDiscovery"
   	]
    def srCount = "No"
	if (scenesToRemove) {
		scenesToRemove.each { k -> state.builtScenes.remove(k)}
	    srCount = "${scenesToRemove.size()}"
	}
    app.updateSetting("scenesToRemove", [])
	return dynamicPage(pageProperties) {
		section {
			paragraph srCount + " scene${(srCount!='1')?'s':' '}/virtual button${(srCount!='1')?'s were':' was'} removed.\n\nTap 'Save'."
		}
	}
}
// Creates a map to enumerate the bridges listed by the LutronPi server & indicate whether each enumerates its own devices (true) or not (false)
private Map bridgeDevicesEnumerated() {
	def allBridges = bridgesLPi
	def bridgemap = [:]
	if (allBridges instanceof java.util.Map) {
		allBridges.each { k, v ->
			bridgemap[k] = v.devicesEnumerated
		}
	}
	return bridgemap
}

// Creates a map to populate the switches pref page
private Map switchesDiscovered() {
	def allSwitches = switches
	def devicemap = [:]
	if (allSwitches instanceof java.util.Map) {
		allSwitches.each { k, v ->
			def value = roomifyLutronDeviceName(v.lRoom, v.name) + (lRoomDeviceNames?'':(v.lRoom ? "?[${v.lRoom.replaceFirst(/\s[rR]oom$/,'')}]":''))
			def key = baseOfDNI(v.dni)
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

// Creates a map to populate the shades pref page
private Map shadesDiscovered() {
	def allShades = shades
	def devicemap = [:]
	if (allShades instanceof java.util.Map) {
		allShades.each { k, v ->
			def value = roomifyLutronDeviceName(v.lRoom, v.name) + (lRoomDeviceNames?'':(v.lRoom ? "?[${v.lRoom.replaceFirst(/\s[rR]oom$/,'')}]":''))
			def key = baseOfDNI(v.dni)
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

// Creates a map to populate the picos pref page
private Map picosDiscovered() {
	def allPicos = picos
	def devicemap = [:]
	if (allPicos instanceof java.util.Map) {
		allPicos.each { k, v ->
			def value = roomifyLutronDeviceName(v.lRoom, v.name) + (lRoomDeviceNames?'':(v.lRoom ? "?[${v.lRoom.replaceFirst(/\s[rR]oom$/,'')}]":''))
			def key = baseOfDNI(v.dni)
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

// Creates a map to populate the scenes pref page
private Map scenesDiscovered() {
	def allScenes = scenes
	def devicemap = [:]
	if (allScenes instanceof java.util.Map) {
		allScenes.each { k, v ->
			def value = v.name as String
			def key = baseOfDNI(v.dni)
			devicemap["${key}"] = value
		}
	}
	return devicemap
}
private baseOfDNI(String dni) {
	def dnicomp = dni?.tokenize('.')
	return (dnicomp?.size()? (dnicomp[0] + '.' + dnicomp[1]) : '')
}

private bridgeOfDNI(String dni) {
	def dnicomp = dni?.tokenize('.')
	return dnicomp? dnicomp[0] : ''
}
private serialNumberOfDNI(String dni) {
	def dnicomp = dni?.tokenize('.')
    return (dnicomp?.size() ? dnicomp[1] : '')
}
private sceneNumberOfDNI(String dni) {
	def dnicomp = dni?.tokenize('.')
    return (dnicomp?.size() ? dnicomp[1] : '')
}

// Returns all found bridges added to app.state (groovy magic: bridgesLPi)
// at the same time, if required, initialize the app.state storage of manually-built devices and scenes
def getBridgesLPi() {
	if (!state.builtDevices) {
		state.builtDevices = [:]
	}
    if (!state.builtScenes) {
		state.builtScenes = [:]
	}
    if (!state.bridgesLPi) {
		state.bridgesLPi = [:]
	}
	state.bridgesLPi
}

// Returns all found switches added to app.state (groovy magic: switches)
def getSwitches() {
	if (!state.switches) {
		state.switches = [:]
	}
	state.switches
}

// Returns all found Picos added to app.state (groovy magic: picos)
def getPicos() {
	if (!state.picos) {
		state.picos = [:]
	}
	state.picos
}

// Returns all found shades added to app.state (groovy magic: shades)
def getShades() {
	if (!state.shades) {
		state.shades = [:]
	}
	state.shades
}

// Returns all found scenes added to app.state (groovy magic: scenes)
def getScenes() {
	if (!state.scenes) {
		state.scenes = [:]
	}
	state.scenes
}

String getLUTRONPI_SCHEMAS() {
	return "schemas-upnp-org:device:LutronPi_BridgeThing:1"
}
String getLUTRONPI_URN() {
	return "urn:${LUTRONPI_SCHEMAS}"
}

void ssdpSubscribe() {
	if (!state.ssdpSubscribed) {
		// subscribe to M-SEARCH answers from LutronPi server
		log.debug "Subscribing to SSDP updates for ${LUTRONPI_URN}"
   		subscribe(location, "ssdpTerm.${LUTRONPI_URN}", ssdpHandler /* , [filterEvents:false] */)
		state.ssdpSubscribed = 'SUBSCRIBED'
	}
}

void ssdpSchedule() {
	runEvery5Minutes("ssdpDiscover")
}

void ssdpOfflineDetect(data) {
	log.debug "Detected possibly offline LutronPi $selectedLPi at ${now()}"
	if ( selectedLPi ) {
		def aLutronPi = lutronPiMap[selectedLPi]
		log.debug "Selected LutronPi $selectedLPi : $aLutronPi"
		if (aLutronPi?.ssdpPath) {	// was online, but now perhaps is not
			if (state.ssdpSubscribed == 'SEARCH') {	// give it one more try
				state.ssdpSubscribed = 'RETRY'
				ssdpDiscover()
				return
			} else
				state.ssdpSubscribed = 'NOREPLY'
			aLutronPi.ssdpPath = ''		// ensure we notice when it comes back up
//			if (notifyOnBridgeUpdate) {
//				log.info "Notification: LutronPi server is offline"
//				userNotification("LutronPi server is offline")
//			}
			def aChildDevice = getChildDevice(selectedLPi)
			if (aChildDevice)
				aChildDevice.sync('','')	// selected LutronPi server offline
		}
	}
}

void ssdpDiscover() {
	log.debug "Discovering SSDP updates for ${LUTRONPI_URN} at ${now()}"
	if (state.ssdpSubscribed != 'RETRY')
		state.ssdpSubscribed = 'SEARCH'
	runIn(60, "ssdpOfflineDetect")	// caution: the ST hub doesn't always catch M-SEARCH replies on the first try
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery ${LUTRONPI_URN}", physicalgraph.device.Protocol.LAN))
}

// Callback when an SSDP M-SEARCH answer is received
def ssdpHandler(evt) {
	runIn(10*60, "ssdpOfflineDetect")	// reschedule the offline checker beyond next discover (cheaper than unschedule?)
	state.ssdpSubscribed = 'SUBSCRIBED'
	def hubId = evt?.hubId
	def description = evt.description
	log.debug "SSDP event at ${now()} name:${evt.name} value:${evt.value}"
	def parsedEvent = parseLanMessage(description)
	// def parsedEvent = parseDiscoveryMessage(description)

	parsedEvent << ["hub":hubId]	// n.b. needed for LAN-connected devices to see unsolicated events via their ST stub
	parsedEvent << ["timestamp":now()]

	// log.debug "SSDP event:" + parsedEvent

	if (parsedEvent?.ssdpTerm?.contains("${LUTRONPI_SCHEMAS}")) {
		parsedEvent << ["ip":convertHexToIP(parsedEvent.networkAddress)]
		parsedEvent << ["port":convertHexToInt(parsedEvent.deviceAddress)]

		def allLutronPiMap = lutronPiMap
		def aLutronPi = allLutronPiMap."${parsedEvent.mac}"
		if (!aLutronPi) { //if the discovered LutronPi isn't already mapped, map it now
			allLutronPiMap << ["${parsedEvent.mac}":parsedEvent]
		} else { // just update the values
			def aChildDevice = getChildDevice(parsedEvent.mac)
			aLutronPi.timestamp = parsedEvent.timestamp
			if (aLutronPi.ip != parsedEvent.ip || aLutronPi.port != parsedEvent.port || aLutronPi.ssdpPath != parsedEvent.ssdpPath) {
				aLutronPi.ip = parsedEvent.ip
				aLutronPi.port = parsedEvent.port
				aLutronPi.ssdpPath = parsedEvent.ssdpPath

				if (aChildDevice) {
					aChildDevice.sync(aLutronPi.ip, aLutronPi.port)
					aChildDevice.label = lutronPiLabel(aLutronPi.ip,aLutronPi.port)
					aChildDevice.data.ssdpUSN = parsedEvent.ssdpUSN
					aChildDevice.data.ssdpPath = parsedEvent.ssdpPath
				}
			}
			if (parsedEvent.mac == selectedLPi) {	// this is the selected server
				if (aChildDevice) {
						if (parsedEvent.ssdpPath != '/connected') {
							subscribeLutronPi()	// re-subscribe to the server if it no longer indicates we're subscribed
							ssdpSchedule		// restart the discovery schedule from now
						}
						else	// do another server summary poll every time SSDP sees the selected LutronPi server (since ST polling is flaky)
							runIn(5, "pollLutronPi") // runIn the poll request to consolidate multiple fast SSDP responses from selected server
				}
			}
		}
	}
}

// Subscribe to the selected LutronPi server and forget about any others previously discovered
// This informs the LutronPi server that it is connected to the SmartApp
private subscribeLutronPi() {
	log.debug "Subscribing to the LutronPi server " + selectedLPi
	def allLutronPiMap = lutronPiMap
    def deleteLPiList = []
	allLutronPiMap.each { k, v ->
		if (k == selectedLPi) {
			sendToLutronPi('PUT','/subscribe', [Hub: "${ HubAddress()}"], lutronPiHandler)
			getAllChildDevices().each { d -> d.refresh() }
		}
		else {
			deleteLPiList << k
		}
	}
	deleteLPiList.each { k ->
		allLutronPiMap.remove(k)
	}
}

private unsubscribeLutronPi() {
	log.debug "Unsubscribing from the LutronPi server " + selectedLPi

	sendToLutronPi('PUT','/unsubscribe', [Hub: "${ HubAddress()}"])
}

def pollLutronPi() {
	log.debug "Polling the LutronPi server " + selectedLPi
	def aChildDevice = getChildDevice(selectedLPi)
	if (aChildDevice) {	// should really only have to do this once on child device creation, but this handles app update cases
		subscribe(aChildDevice, "commStatus", lutronPiCommStatusHandler)
	}
	sendToLutronPi('GET','/poll', [:], lutronPiHandler)
}

/* The poll/update response from the LutronPi server mimics the Lutron response format:
 * {
 * "CommuniqueType":"ServerResponse",
 * "Header":{"MessageBodyType":"LutronPiStatus","StatusCode":"200 OK","Url":"/poll"},
 * "Body":{"Bridges":{bridge0SN: {map of bridge 0 data}, bridge1SN: {map of bridge 1 data}...}
 * }
 */
def lutronPiPollResponse(jsonMsgBody) {
//	log.debug "got a LutronPi server summary poll: " + jsonMsgBody
	def aChildDevice = getChildDevice(selectedLPi)
	if (!aChildDevice)
		return;		// this server's corresponding child device hasn't been created yet, so skip this update
	def aLutronPi = lutronPiMap[selectedLPi]
	if (aChildDevice.currentValue('commStatus') != 'ONLINE') {
			aChildDevice.sync(aLutronPi?.ip, aLutronPi?.port)
	}
	def bridgeMap = jsonMsgBody?.Bridges;
	if (bridgeMap != null && (bridgeMap.size() > 0)) {
		log.debug "LutronPi [${jsonMsgBody.Version}] bridge summary: " + bridgeMap
		aChildDevice.bridgeUpdate(jsonMsgBody)
		def bridgeUpdatedList = []
		def bridgeGoneOfflineList = []
		// acquire & save the server's bridge info, including a hash to compare for indication of Lutron update
		if (aLutronPi?.poll) {	// compare this poll to previous poll, if any
			bridgeMap.each { b, p ->
				def prevBridgePoll = aLutronPi.poll?.bridges[b]
				if (prevBridgePoll) {
					if (prevBridgePoll?.Connected && !p?.Connected) {
						log.info "A Lutron bridge ${b} has gone offline"
						bridgeGoneOfflineList.push(b as String)
					}
					else if (prevBridgePoll?.Digest != p?.Digest) {
						log.info "There is a Lutron bridge ${b} update available"
						bridgeUpdatedList.push(b as String)
					}
				} else {	// losing / regaining a bridge is not an update, since it could drop offline temporarily
					log.info "There is a new Lutron bridge ${b} available"
					bridgeUpdatedList.push(b as String)
				}
			}
		}
		aLutronPi.poll = [timestamp: now(), bridges: bridgeMap]
		if (jsonMsgBody?.Version)
			aLutronPi.poll.server = jsonMsgBody.Version;
		if (notifyOnBridgeUpdate) {
			if (bridgeGoneOfflineList.size() > 0) {
				log.info "Notification: LutronPi bridge(s) are offline"
				userNotification("LutronPi bridge(s) are offline: ${bridgeGoneOfflineList}")
			}
			if (bridgeUpdatedList.size() > 0) {
				log.info "Notification: LutronPi bridge updates available"
				userNotification("LutronPi bridge updates available: ${bridgeUpdatedList}")
			}
		}
	}
}

def lutronPiHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "at LutronPi server poll handler"
	lutronPiPollResponse(hubResponse.json?.Body)
}

private String roomifyLutronDeviceName(lRoom, lName) {
	return ((lRoomDeviceNames && lRoom) ? (lRoom + ':' + lName) : lName) as String
}

// Request device list from LutronPi server
private discoverBridgeDevices() {
	log.debug "Discovering your LutronPi Devices"

   // Get switches and picos and add to state
	sendToLutronPi('GET','/devices', [:], lutronDeviceHandler)
}

String dniLDevice(String bridgeRef, String deviceRef='', Boolean baseDNI=false) {
	if (!bridgeRef)
    	bridgeRef = 'LutronPi'
	return (bridgeRef + (deviceRef?('.' + deviceRef):'') + (baseDNI?'':'.LPiD'))
}

String dniLScene(String bridgeRef, String sceneRef='', Boolean baseDNI=false) {
	if (!bridgeRef)
    	bridgeRef = 'LutronPi'
	return (bridgeRef + (sceneRef?('.' + sceneRef):'') + (baseDNI?'':'.LPiS'))
}

// Handle device list request response from LutronPi server
def lutronDeviceHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "at the LutronPi device list handler"

	def body = hubResponse.json
	if (body != null) {
		log.debug body
		def allBridges = bridgesLPi
		allBridges.clear()

		def allSwitches = switches
		allSwitches.clear()
	    allSwitches << state.builtDevices.findAll { k,v -> v.deviceType && ['WallDimmer','PlugInDimmer','WallSwitch'].contains(v.deviceType) }

		def allShades = shades
		allShades.clear()
    	allShades << state.builtDevices.findAll { k,v -> v.deviceType && (v.deviceType?.endsWith('Shade')) }

		def allPicos = picos
		allPicos.clear()
    	allPicos << state.builtDevices.findAll { k,v -> v.deviceType && (v.deviceType?.startsWith('Pico') || v.deviceType == 'Keypad') }

		body.each { k ->
			log.debug k
			def lBridge =  k.Bridge ? k.Bridge.toString() : ''
            def lipID = k.ID ?: 0;
            if (lBridge && lipID == 1) {
            	allBridges[lBridge] = [devicesEnumerated: !k.NoDeviceList, type: k.DeviceType]
			} else {
				def lRoom = ((k.FullyQualifiedName?.size() > 1) && (k.FullyQualifiedName[1] == k.Name)) ? k.FullyQualifiedName[0] : ''
                def zone = k.Zone ?: (k.LocalZones ? (k.LocalZones[0].href?.replaceFirst(/(?i)\/zone\//,'') ?: 0) : 0)
				def devSpec = createDeviceSpec(lBridge, k.DeviceType, lipID, zone, k.Name, lRoom, k.SerialNumber)
				switch (devSpec.deviceType) {
					case ['WallDimmer','PlugInDimmer','WallSwitch']:
						allSwitches[devSpec.dni] = devSpec
						break
					case { it?.endsWith('Shade') }:		//['SerenaHoneycombShade','SerenaRollerShade','TriathlonHoneycombShade','TriathlonRollerShade','QsWirelessShade']
						allShades[devSpec.dni] = devSpec
						break;
					case { it?.startsWith('Pico') }:		// "Pico2Button*" || "Pico3Button*" || "Pico4Button*" etc.
						allPicos[devSpec.dni] = devSpec
						break;
				}
			}
		}
	}
}

private Map createDeviceSpec(lBridge, lDeviceType, lipID, lZoneOrMaxBtn, lName, lRoom, lSerialNumber) {
	def baseDNI = dniLDevice(lBridge, lSerialNumber.toString(), true)
	def deviceSpec = [:]
	switch (lDeviceType) {
		case ['WallDimmer','PlugInDimmer','WallSwitch']:
        case { it?.endsWith('Shade') }:		//['SerenaHoneycombShade','SerenaRollerShade','TriathlonHoneycombShade','TriathlonRollerShade','QsWirelessShade']
			deviceSpec << [name: lName, lipID: lipID, zone: lZoneOrMaxBtn, dni: baseDNI, deviceType: lDeviceType]
			if (lRoom)
            	deviceSpec << [lRoom: lRoom]
			break
		case 'Keypad':
				deviceSpec << [maxButton: lZoneOrMaxBtn]
		case { it?.startsWith('Pico') }:		// "Pico2Button*" || "Pico3Button*" || "Pico4Button*" etc.
			deviceSpec << [name: lName, lipID: lipID, dni: baseDNI, deviceType: lDeviceType]
			if (lRoom)
            	deviceSpec << [lRoom: lRoom]
			break
	}
	return deviceSpec
}

private discoverBridgeScenes() {
	log.debug "Discovering your LutronPi Scenes"

	// Get scenes and add to state
	sendToLutronPi('GET','/scenes', [:], lutronSceneHandler)
}

// Handle scene list request response from LutronPi server
def lutronSceneHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "at the LutronPi scene list handler"
	def allScenes = scenes
	allScenes.clear()
    allScenes << state.builtScenes

	def body = hubResponse.json
	if (body != null) {
		log.debug body

		body.each { k ->
        	def newScene = createSceneSpec(k.Bridge ? k.Bridge.toString() : '', k.href.replaceFirst(/(?i)\/virtualbutton\//,''), k.Name);
			allScenes[newScene.dni] = newScene
		}
	}
	log.debug allScenes
    log.debug scenes
}

private Map createSceneSpec(lBridge, virtButtonNum, name) {
	return [name: name, virtualButton: virtButtonNum, dni: dniLScene(lBridge, virtButtonNum.toString(), true)]
}

// Generate the list of LutronPi servers for the preferences dialog
def getLutronPiListForDialog() {
	def allLutronPiMap = lutronPiMap
	def lutronPiDialogMap = [:]
	allLutronPiMap.each {
		def value = it.value.ip + ':' + it.value.port
		def key = it.value.mac
		lutronPiDialogMap["${key}"] = value
	}
	lutronPiDialogMap
}

// Get map containing discovered Lutron Pi Servers
def getLutronPiMap() {
	if (!state.lutronPiMap) { state.lutronPiMap = [:] }
	state.lutronPiMap
}

// manage notifications
def userNotification(msg) {
		sendPush(msg);
}

private String HubAddress() {
	def stHub = location.hubs.find{it.id == lutronPiMap[selectedLPi].hub}
	"${stHub.localIP}:${stHub.localSrvPortTCP}"
}

def uninstalled() {
	if (lutronPiMap != [:] && selectedLPi)
		unsubscribeLutronPi()
	log.info "The ${app.name} SmartApp is being uninstalled"
}

def installed() {
	log.info "The ${app.name} SmartApp is installed"
	initialize()
}

def updated() {
	log.info "The ${app.name} SmartApp is updated"
	initialize()
}

def initialize() {
	unschedule()
	unsubscribe()
  	state.ssdpSubscribed = false

	log.info "The ${app.name} SmartApp is initializing with settings: ${settings}"

	log.info "ST Hub at ${HubAddress()}"

	// clean up devices/scenes previously selected but no longer present (phantoms) and existing devices now deselected
	// note that preference inputs (settings map) is read-only, so selectedXXX lists cannot be programmatically modified
	def activeDNIList
	def someDevices

	def listChildDevices = getAllChildDevices().findAll { it.deviceNetworkId != selectedLPi }

	someDevices = switches
	activeDNIList = selectedSwitches.findAll { k -> !!someDevices[k] }
	listChildDevices.eachWithIndex {acd, acdix->
		if (['Lutron Dimmer','Lutron Switch'].contains(acd?.typeName)) {
            if (!activeDNIList.contains(baseOfDNI(acd.deviceNetworkId)))
				deleteChildDeviceSafe(acd)
			listChildDevices[acdix] = null
		}
	}

	someDevices = shades
	activeDNIList = selectedShades.findAll { k -> !!someDevices[k] }
	listChildDevices.eachWithIndex {acd, acdix->
		if (acd?.typeName?.startsWith('Lutron Shade')) {
            if (!activeDNIList.contains(baseOfDNI(acd.deviceNetworkId)))
            	deleteChildDeviceSafe(acd)
			listChildDevices[acdix] = null
		}
	}

	someDevices = picos
	activeDNIList = selectedPicos.findAll { k -> !!someDevices[k] }
	listChildDevices.eachWithIndex {acd, acdix->
		if (acd?.typeName?.startsWith('Lutron Pico') || acd?.typeName?.startsWith('Lutron Keypad')) {
            if (!activeDNIList.contains(baseOfDNI(acd.deviceNetworkId)))
            	deleteChildDeviceSafe(acd)
			listChildDevices[acdix] = null
		}
	}

	someDevices = scenes
	activeDNIList = selectedScenes.findAll { k -> !!someDevices[k] }
	listChildDevices.eachWithIndex {acd, acdix->
		if (acd?.typeName?.startsWith('Lutron Scene')) {
            if (!activeDNIList.contains(baseOfDNI(acd.deviceNetworkId)))
            	deleteChildDeviceSafe(acd)
			listChildDevices[acdix] = null
		}
	}
	if (listChildDevices.any {acd -> acd})
		log.warn "Unidentified child devices are: " + listChildDevices.findResults { acd -> acd?.deviceNetworkId }

	// If a LutronPi server was actually selected add the child devices
	if (selectedLPi) {
		if (addLutronPi()) {
			addDevices('switches')
			addDevices('shades')
			addPicos()
			addScenes()
		}
	}

	ssdpSubscribe()
	ssdpSchedule()
}

private deleteChildDeviceSafe(aChildDevice) {
	try {
		deleteChildDevice(aChildDevice.deviceNetworkId)
        log.info "Deleted LutronPi child device with DNI:${aChildDevice.deviceNetworkId}."
        return null
	} catch (e) {
		log.warn "Unable to delete LutronPi child device with DNI:${aChildDevice.deviceNetworkId}; probably still in use elsewhere. [ERR=$e]"
        return e
	}
}

String lutronPiLabel(dispIP = null, dispPort = null) {
	'LutronPi Server'
}

def lutronPiCommStatusHandler(evt) {
	if (notifyOnBridgeUpdate && evt.isStateChange) {
		log.info "Notification: LutronPi server is ${evt.value.toLowerCase()}"
		userNotification("LutronPi server is ${evt.value.toLowerCase()}")
	}
}

Boolean addLutronPi() {	// return success=true or failure=false
	def stDNI = selectedLPi
	def aLutronPi = lutronPiMap[selectedLPi]

	// having just completed a manual devices/scenes update, reset the update polling data
	if (aLutronPi.poll)
		aLutronPi.poll = null;

	// Check if child already exists
	def aChildDevice = getChildDevice(selectedLPi)
	// Add the LutronPi if new - otherwise the SSDP handler will deal with IP:Port and Path changes, if any
	if (!aChildDevice) {
		def aLutronPiLabel = lutronPiLabel(aLutronPi.ip, aLutronPi.port)

		def deviceSpec = [nameSpace: 'lutronpi', deviceType: 'LutronPi Bridge Server']
		try {
			log.info "Adding $aLutronPiLabel server: DNI $stDNI"
			aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, stDNI, aLutronPi.hub, [
						"label": aLutronPiLabel,
						"data": [
								'ip': aLutronPi.ip,
								'port': aLutronPi.port,
								'ssdpUSN': aLutronPi.ssdpUSN,
								'ssdpPath': aLutronPi.ssdpPath
								]
			])
			pollLutronPi()	// do an initial server summary poll; let the SSDP handler take it from here
            return true
		} catch (e) {
			log.error "Unable to add LutronPi device as ${deviceSpec.nameSpace}:${deviceSpec.deviceType} with DNI $stDNI [ERR=$e]"
            return false
		}
	}
    return true
}

def addDevices(String devgroup) {
	def dName
	def lRoom
	def zone
	def lipID
	def deviceType
	def baseDNI, stDNI
	def aChildDevice
	def deviceData

	def allDevices
	def selectedDevices
	switch (devgroup) {
		case 'switches':
			allDevices = switches
			selectedDevices = selectedSwitches.findAll { k-> allDevices[k] }
			break
		case 'shades':
			allDevices = shades
			selectedDevices = selectedShades.findAll { k-> allDevices[k] }
			break
		default:
			return
	}

	selectedDevices?.each { k ->
		log.debug "Selected $devgroup device: " + allDevices[k]

		lRoom = allDevices[k].lRoom ?: ''
		dName = roomifyLutronDeviceName(lRoom, allDevices[k].name)
		zone = allDevices[k].zone
		lipID = allDevices[k].lipID
		deviceType = allDevices[k].deviceType
		baseDNI = allDevices[k].dni
        stDNI = dniLDevice(baseDNI)

		deviceData = [
						'zone': zone,
						'lipID': lipID,
						'deviceType': deviceType,
		             ]
		if (lRoom)
			deviceData << ['lRoom': lRoom]

		// Check if child device already exists
		aChildDevice = getChildDevice(stDNI)
        if (!aChildDevice)		// also check for legacy DNI if necessary
			aChildDevice = getChildDevice(baseDNI)

		if (aChildDevice) {	// just update device data, e.g. name or ID changes
			if (!lockSTDeviceNames)
				aChildDevice.label = dName
			deviceData.each { kk, vv -> aChildDevice.data[kk] = vv }
			log.info "Updated ${aChildDevice.label} ($deviceType) with DNI ${aChildDevice.deviceNetworkId}"
		} else {			// create a new device
			def deviceSpec = [:]
			switch (deviceType) {
				case ['WallDimmer','PlugInDimmer']:
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Dimmer']
					break;
				case ['WallSwitch']:
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Switch']
					break;
				case { it?.endsWith('Shade') }:		//['SerenaHoneycombShade','SerenaRollerShade','TriathlonHoneycombShade','TriathlonRollerShade','QsWirelessShade']
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Shade']
					break;
			}
			if (deviceSpec) {
				try {
					log.info "Adding $dName ($deviceType) as lipID $lipID/Zone $zone with DNI $stDNI"
					aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, stDNI, null, [
						'label': dName,
						'data': deviceData
					])
				} catch (e) {
					log.error "Unable to add $dName as ${deviceSpec.nameSpace}:${deviceSpec.deviceType} with DNI $stDNI [ERR=$e]"
				}
			}
		}
		if (aChildDevice) {
			// Call refresh on the new or updated device to get the current state
			aChildDevice.refresh()
		}
	}
}

def addPicos() {
	def dName
	def lRoom
	def lipID
	def deviceType
    def maxButton
	def baseDNI, stDNI
	def aChildDevice
	def deviceData

	def allPicos = picos
	def	selectedDevices = selectedPicos.findAll { k-> allPicos[k] }

	selectedDevices?.each { k ->
		log.debug "Selected Pico/Keypad: " + allPicos[k]

		lRoom = allPicos[k].lRoom ?: ''
		dName = roomifyLutronDeviceName(lRoom, allPicos[k].name)
		lipID = allPicos[k].lipID
		deviceType = allPicos[k].deviceType
        maxButton = (allPicos[k].maxButton?:0) as Integer
		baseDNI = allPicos[k].dni
		stDNI = dniLDevice(baseDNI)

		deviceData = [
						'lipID': lipID,
						'deviceType': deviceType,
		             ]
		if (lRoom)
			deviceData << ['lRoom': lRoom]
		if (maxButton)
			deviceData << ['maxButton': maxButton]

		// Check if child already exists
		aChildDevice = getChildDevice(stDNI)
		if (!aChildDevice)		// also check for legacy DNI if necessary
			aChildDevice = getChildDevice(baseDNI)

		if (aChildDevice) {	// just update device data, e.g. name or ID changes
			if (!lockSTDeviceNames)
				aChildDevice.label = dName
			deviceData.each { kk, vv -> aChildDevice.data[kk] = vv }
			log.info "Updated ${aChildDevice.label} ($deviceType) with DNI ${aChildDevice.deviceNetworkId}"
		} else {			// create a new device
			def deviceSpec = [:]
			switch (deviceType) {
				case 'Pico2Button':
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Pico 2B']
					break;
				case 'Pico2ButtonRaiseLower':
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Pico 2BRL']
					break;
				case 'Pico3Button':
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Pico 3B']
					break;
				case 'Pico3ButtonRaiseLower':
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Pico 3BRL']
					break;
				case ['Pico4Button', 'Pico4ButtonScene', 'Pico4ButtonZone', 'Pico4Button2Group']:
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Pico 4B']
					break;
				case 'Keypad':
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Keypad']
					break;
			}
			if (deviceSpec) {
				try {
					log.info "Adding $dName ($deviceType) with lipID $lipID with DNI $stDNI"
					aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, stDNI, null, [
							'label': dName,
							'data': deviceData
					])
				} catch (e) {
					log.error "Unable to add $dName as ${deviceSpec.nameSpace}:${deviceSpec.deviceType} with DNI $stDNI [ERR=$e]"
				}
			}
		}
		if (aChildDevice) {
			// Call refresh on the new or updated device to get the current state
			aChildDevice.refresh()
		}
	}
}

def addScenes() {
	def dName
	def virtButton
	def baseDNI, stDNI
	def aChildDevice
	def deviceData

	def allScenes = scenes
	def	selectedDevices = selectedScenes.findAll { k-> allScenes[k] }

	selectedDevices?.each { k ->
		log.debug "Selected scene: " + allScenes[k]
		dName = allScenes[k].name
		virtButton = allScenes[k].virtualButton
 		baseDNI = allScenes[k].dni
		stDNI = dniLScene(baseDNI)

		deviceData = [
					'virtualButton': virtButton,
		             ]

		// Check if child already exists
		aChildDevice = getChildDevice(stDNI)
		if (!aChildDevice)		// also check for legacy DNI if necessary
			aChildDevice = getChildDevice(baseDNI)

		if (aChildDevice) {	// just update device data, e.g. name changes
			if (!lockSTDeviceNames)
				aChildDevice.label = dName
			deviceData.each { kk, vv -> aChildDevice.data[kk] = vv }
			log.info "Updated ${aChildDevice.label} (Scene) with DNI ${aChildDevice.deviceNetworkId}"
		} else {			// create a new device
			def deviceSpec = [nameSpace: 'lutronpi', deviceType: 'Lutron Scene']
			try {
				log.info "Adding the scene $dName with DNI $stDNI"
				aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, stDNI, null, [
					'label': dName,
					'data': deviceData
				])
				// no initial refresh required since we cannot keep track of Lutron scene status
			} catch (e) {
				log.error "Unable to add scene $dName as ${deviceSpec.nameSpace}:${deviceSpec.deviceType} with DNI $stDNI [ERR=$e]"
			}
		}
	}
}

////////////////////////////////////////////////////////////////////////////
//						CHILD DEVICE FUNCTIONS							 //
///////////////////////////////////////////////////////////////////////////

// Parse the data from LutronPi. This is called by the LutronPi server device type parse method because that device
// receives all the LAN updates sent from the LutronPi back to the hub
def parse(description) {

//	log.debug description
	switch (description?.Header?.MessageBodyType) {
		case 'LutronPiNotify':
			// if we get this message, the LutronPi has just restarted and we must hasten to rediscover it and refresh all
			log.info "LutronPi appears to have restarted; re-discovering..."
			ssdpDiscover()
			return
			break
		case 'OneZoneStatus':
			// Get the bridge and zone and level from the received message (a zone can have one device, but a device can have multiple zones)
			def lBridge = (description?.Header?.Bridge ?: '') as String
			def zone = description?.Body?.ZoneStatus?.Zone?.href?.replaceFirst(/(?i)\/zone\//,'') as Integer
			def level = description?.Body?.ZoneStatus?.Level ?: 0
			// Match the bridge and zone to a child device and determine its DNI in order to send event to appropriate device
			if (zone) {
				def aChildDevice =
					getAllChildDevices().find { d->
						((d.getDataValue('zone') as Integer) == zone) && (bridgeOfDNI(d.device.deviceNetworkId as String) == lBridge)
					}
				if (aChildDevice) {
					def eventLinkText = "${aChildDevice.label} level to $level"
					def descrText = eventLinkText
					log.debug eventLinkText
					sendEvent(	aChildDevice,
								[ name: 'level', value: level,
								  descriptionText: descrText,
								  isStateChange: true,
								  linkText: eventLinkText
								] )
					break
				}
			}
			log.warn "LutronPi referenced unknown zone $zone"
			break
		case 'ButtonAction':
			Boolean isScene = false
			def dni
			def aChildDevice
			def lBridge = (description?.Header?.Bridge ?: '') as String
			def lDeviceSN = (description?.Body?.SerialNumber ?: '') as String
			def button = description?.Body?.Button
			def action = description?.Body?.Action

			for (def tryLegacyDNI = 0; tryLegacyDNI <= 1; tryLegacyDNI++) {
				if (lDeviceSN) {
					if (lBridge == lDeviceSN) {	// if bridge == device, it's a scene
						isScene = true;
						dni = dniLScene(lBridge, button as String, tryLegacyDNI as Boolean)
					}
					else					// else it's a Pico
						dni = dniLDevice(lBridge, lDeviceSN, tryLegacyDNI as Boolean)
					aChildDevice = getChildDevice(dni)
				} else {					// lacking a device SN, try looking up the device by LIP ID
					def lipID
					try { lipID = (description?.Body?.ID ?: 0) as Integer } catch (e) { lipID = 0 }
					if (lipID == 1) {		// if ID=1, that's a bridge, so it's a scene
						isScene = true;
						dni = dniLScene(lBridge, button as String, tryLegacyDNI as Boolean)
						aChildDevice = getChildDevice(dni)
					} else
						aChildDevice =
							getAllChildDevices().find { d ->
								((d.getDataValue('lipID') as Integer) == lipID) && (bridgeOfDNI(d.device.deviceNetworkId as String) == lBridge)
							}
				}
				if (aChildDevice) {
					def eventLinkText = (isScene?'Scene':'Pico') + " ${aChildDevice.label}:$button $action"
					def descrText = eventLinkText
					log.debug eventLinkText
					sendEvent(	aChildDevice,
								[ name: 'button', value: action, data: [buttonNumber: button],
								  descriptionText: descrText,
								  isStateChange: true,
								  linkText: eventLinkText
								] )
					break
				}
			}
			if (tryLegacyDNI > 1)
				log.warn "LutronPi referenced unknown ${isScene?'scene':'Pico'}: $lBridge:$lDeviceSN:$button"
			break
		default:
			log.warning "Unknown communique from LutronPi: $description"
			break
	}
}

private Map buildLPiBaseRequest(stDevice) {
	def req = [bridge: bridgeOfDNI(stDevice.device.deviceNetworkId)]
	def devID = stDevice.getDataValue('lipID')
	if (devID)
		req << [deviceID: devID]
	def zone = stDevice.getDataValue('zone')
	if (zone)
		req << [zone: zone as Integer]
	return req
}

// Send refresh request to LutronPi to get requested dimmer/switch (zone) status
def refresh(zoneDevice) {
	log.debug "refresh by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST', '/status' ,buildLPiBaseRequest(zoneDevice))
}

// Send request to turn zone on (on assumes level of 100)
def on(zoneDevice, rate = null) {
	log.debug "ON by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST', '/on', buildLPiBaseRequest(zoneDevice) + [fadeSec: (rate?:0)])
}

// Send request to turn zone off (level 0)
def off(zoneDevice, rate = null) {
	setLevel(zoneDevice, 0, rate)
}

// Send request to fade zone raise/lower/stop
def fadeLevel(zoneDevice, fadeCmd) {
	log.debug "fadeLevel $fadeCmd by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST', '/setLevel', buildLPiBaseRequest(zoneDevice) + [level:fadeCmd])
}

// Send request to set device to a specific level, optionally with a specified fade time (only works on Pro bridges)
def setLevel(zoneDevice, level, rate = null) {
	log.debug "setLevel by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST', '/setLevel', buildLPiBaseRequest(zoneDevice) + [level: level, fadeSec: (rate?:0)])
}

def runScene(sceneDevice) {
	log.debug "Scene ${sceneDevice.device.getDisplayName()} ${sceneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST', '/scene', buildLPiBaseRequest(sceneDevice) +
	                                 [virtualButton: sceneNumberOfDNI(sceneDevice.device.deviceNetworkId) as Integer])
}

def buttonPoke(buttonDevice, buttonButton, action) {
	log.debug "Button ${buttonDevice.device.getDisplayName()} ${buttonDevice.device.deviceNetworkId} (lipID=${buttonDevice.getDataValue("lipID")}) button=$buttonButton action=$action sent to LutronPi"
	sendToLutronPi('POST', '/button', buildLPiBaseRequest(buttonDevice) +
	                                  [deviceSN: serialNumberOfDNI(buttonDevice.device.deviceNetworkId), buttonNumber: buttonButton, action: action])
}

def buttonMode(buttonDevice, buttonModeMap) {
	log.debug "Button ${buttonDevice.device.getDisplayName()} ${buttonDevice.device.deviceNetworkId} (lipID=${buttonDevice.getDataValue("lipID")}) buttons=$buttonModeMap sent to LutronPi"
	sendToLutronPi('POST', '/buttonmode', buildLPiBaseRequest(buttonDevice) +
	                                      [deviceSN: serialNumberOfDNI(buttonDevice.device.deviceNetworkId),
	                                       pushTime: picoShortPushTime, repeatTime: picoRepeatHoldIntervalTime, buttons: buttonModeMap])
}

def renameChildDevice(aChildDevice, newDeviceName) {
	// placeholder for now
}

private sendToLutronPi(String httpVerb, String httpPath, content = [:], callback = null) {
	if (!httpVerb || !httpPath)
		return

	def aLutronPi = lutronPiMap[selectedLPi]
	def headers =
	    [HOST: aLutronPi.ip + ':' + aLutronPi.port,
	     'Content-Type': 'application/json'
	    ]

	def sendHubAction = new physicalgraph.device.HubAction(
		[method: httpVerb,
		 path: httpPath,
		 body: content,
		 headers: headers
		], "${selectedLPi}", callback?[callback: callback]:null
	)
	sendHubCommand(sendHubAction)
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
