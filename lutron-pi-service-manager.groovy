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
		description: "Interface to talk with Lutron SmartBridge and add Pico Remotes v1+",
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
	page(name:"sceneDiscovery", title:"Lutron Scene Setup", content:"sceneDiscovery")
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
	// Check to see if the LutronPi Server already (and still) exists; if not load LutronPi discovery, else skip discovery
	if (lutronPiMap.size() == 1 && selectedLPi) {
		if (lPiReDiscovery(lutronPiMap[selectedLPi]))
			return lPiFirstSelected()
	}
	return lPiDiscovery()
}

def lPiFirstSelected() {
//	log.debug "Made it to lPiFirstSelected()"
	// subscribe to the server when the server is first selected, before setting options and discovering devices
	if (lutronPiMap != [:] && selectedLPi) {
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
	def refreshInterval = 1

	// Populate the preferences page with found devices
	def lutronPiSelectList = lutronPiListForDialog
	if (lutronPiSelectList != [:]) {
//		if ((lutronPiSelectList.size() == 1) && selectedLPi)
//			return lPiFirstSelected()	// if there's only one and it's been selected... let's go!
		refreshInterval = 5
	}
	else {
		// Perform LutronPi server LAN search vis SSDP
		ssdpSubscribe()
		log.debug 'Performing initial LutronPi discovery'
		ssdpDiscover()
	}

	return dynamicPage(name:"lPiDiscovery", title:"LutronPi Server Discovery", nextPage:"lPiFirstSelected", refreshInterval: refreshInterval, uninstall: true) {
		section(hideWhenEmpty: true, "Select your LutronPi Server") {
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
	// log.debug selectedSwitches
	// log.debug selectedPicos
	// log.debug selectedShades

	def switchOptions = switchesDiscovered()
	def picoOptions = picosDiscovered()
	def shadeOptions = shadesDiscovered()

	def switchPhantoms = selectedSwitches.findAll { k-> !switchOptions[k]}
	def picoPhantoms = selectedPicos.findAll { k-> !picoOptions[k]}
	def shadePhantoms = selectedShades.findAll { k-> !shadeOptions[k]}

	// Populate the preferences page with found devices
	discoverLutronDevices()

	if (!selectorFlag.initSwitch) {
		refreshInterval = 10
	} else
		selectorFlag.initSwitch = false;

	log.debug "Made it to deviceDiscovery preferences"
	return dynamicPage(name:"deviceDiscovery", title:"Device Discovery", nextPage:"sceneDiscovery", refreshInterval: refreshInterval, uninstall: false) {

		section(hideWhenEmpty: true, "Switches") {
			input "selectedSwitches", "enum", required:false, submitOnChange:true, title:"Select Switches \n(${selectedSwitches?.size()?:0} of ${switchOptions.size()?:0} found)",
			      multiple:true, options:switchOptions, image: "http://i63.tinypic.com/kb3jty.png"
		}

		section(hideWhenEmpty: true, "Shades") {
			input "selectedShades", "enum", required:false, submitOnChange:true, title:"Select Shades \n(${selectedShades?.size()?:0} of ${shadeOptions.size()?:0} found)",
			      multiple:true, options:shadeOptions, image: "http://oi68.tinypic.com/2e2dk4z.jpg"
		}
 		
		section(hideWhenEmpty: true, "Picos") {
			input "selectedPicos", "enum", required:false, submitOnChange:true, title:"Select Picos \n(${selectedPicos?.size()?:0} of ${picoOptions.size()?:0} found)",
			      multiple:true, options:picoOptions, image: "http://i67.tinypic.com/4hujyv.png"

			paragraph "Pico buttons can only be monitored from a Lutron Caséta Pro SmartBridge or RA2 Select Repeater. Picos on a standard SmartBridge cannot control devices in SmartThings.",
					  hideWhenEmpty: "selectedPicos"
		}

		section(hideWhenEmpty: true, "Deleted / Please de-select:") {
			input "selectedSwitches", "enum", required:false, submitOnChange:true, title:"De-select phantom switches",
			      multiple:true, options:switchPhantoms, image: "st.custom.buttons.subtract-icon"
			input "selectedShades", "enum", required:false, submitOnChange:true, title:"De-select phantom shades",
			      multiple:true, options:shadePhantoms, image: "st.custom.buttons.subtract-icon"
			input "selectedPicos", "enum", required:false, submitOnChange:true, title:"De-select phantom Picos",
			      multiple:true, options:picoPhantoms, image: "st.custom.buttons.subtract-icon"
		}
	}
}

// Preferences page to add Lutron scenes
def sceneDiscovery() {
//	log.debug "Made it to sceneDiscovery()"
	def refreshInterval = 2
	// log.debug selected Scenes

	def sceneOptions = scenesDiscovered()

	def scenePhantoms = selectedScenes.findAll { k-> !sceneOptions[k]}

	// Populate the preferences page with found scenes
	discoverLutronScenes()

	if (!selectorFlag.initScene /* sceneOptions != [:] */) {
		refreshInterval = 10
	} else
		selectorFlag.initScene = false;

	return dynamicPage(name:"sceneDiscovery", title:"Scene Discovery", nextPage:"", refreshInterval: refreshInterval, install: true, uninstall: false) {
		section(hideWhenEmpty: true, "Select Lutron scenes") {
			input "selectedScenes", "enum", required:false, submitOnChange:true, title:"Select Scenes \n(${selectedScenes?.size()?:0} of ${sceneOptions?.size()?:0} found)",
			      multiple:true, options:sceneOptions,image: "http://oi65.tinypic.com/znwu9i.jpg"
		}

		section(hideWhenEmpty: true, "Deleted / Please de-select:") {
			input "selectedScenes", "enum", required:false, submitOnChange:true, title:"De-select phantom scenes",
			      multiple:true, options:scenePhantoms, image: "st.custom.buttons.subtract-icon"
		}
	}
}

// Creates a map to populate the switches pref page
Map switchesDiscovered() {
	def allSwitches = switches
	def devicemap = [:]
	if (allSwitches instanceof java.util.Map) {
		allSwitches.each { k, v ->
			def value = roomifyLutronDeviceName(v.lRoom, v.name) + (lRoomDeviceNames?'':(v.lRoom ? "?[${v.lRoom.replaceFirst(/\s[rR]oom$/,'')}]":''))
			def key = v.dni
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

// Creates a map to populate the picos pref page
Map picosDiscovered() {
	def allPicos = picos
	def devicemap = [:]
	if (allPicos instanceof java.util.Map) {
		allPicos.each { k, v ->
			def value = roomifyLutronDeviceName(v.lRoom, v.name) + (lRoomDeviceNames?'':(v.lRoom ? "?[${v.lRoom.replaceFirst(/\s[rR]oom$/,'')}]":''))
			def key = v.dni
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

// Creates a map to populate the shades pref page
Map shadesDiscovered() {
	def allShades = shades
	def devicemap = [:]
	if (allShades instanceof java.util.Map) {
		allShades.each { k, v ->
			def value = roomifyLutronDeviceName(v.lRoom, v.name) + (lRoomDeviceNames?'':(v.lRoom ? "?[${v.lRoom.replaceFirst(/\s[rR]oom$/,'')}]":''))
			def key = v.dni
			devicemap["${key}"] = value
		}
	}
	return devicemap
}

// Creates a map to populate the scenes pref page
Map scenesDiscovered() {
	def allScenes = scenes
	def devicemap = [:]
	if (allScenes instanceof java.util.Map) {
		allScenes.each { k, v ->
			def value = v.name as String
			def key = v.dni
			devicemap["${key}"] = value
		}
	}
	return devicemap
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
	if ( selectedLPi ) {
		def aLutronPi = lutronPiMap[selectedLPi]
		if (aLutronPi?.ssdpPath) {	// was online, but now perhaps is not
			if (state.ssdpSubscribed == 'SEARCH') {	// give it one more try
				state.ssdpSubscribed = 'RETRY'
				ssdpDiscover()
				return
			} else
				state.ssdpSubscribed = 'NOREPLY'
			aLutronPi.ssdpPath = ''		// ensure we notice when it comes back up
			if (notifyOnBridgeUpdate) {
				log.info "Notification: LutronPi server is offline"
				userNotification("LutronPi server is offline")
			}
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
		if (!(allLutronPiMap."${parsedEvent.mac}")) { //if the discovered LutronPi isn't already mapped, map it now
			allLutronPiMap << ["${parsedEvent.mac}":parsedEvent]
		} else { // just update the values
			def aLutronPi = allLutronPiMap."${parsedEvent.mac}"
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
						else
							pollLutronPi()	// do another server summary poll every time SSDP sees the selected LutronPi server (since ST polling is flaky)
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
	allLutronPiMap.each { k, v ->
		if (k == selectedLPi) {
			sendToLutronPi('PUT','/subscribe', [Hub: "${ HubAddress()}"], lutronPiHandler)
			getAllChildDevices().each { d -> d.refresh() }
		}
		else {
			allLutronPiMap[k].remove
		}
	}
}

private unsubscribeLutronPi() {
	log.debug "Unsubscribing from the LutronPi server " + selectedLPi

	sendToLutronPi('PUT','/unsubscribe', [Hub: "${ HubAddress()}"])
}

def pollLutronPi() {
	log.debug "Polling the LutronPi server " + selectedLPi

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
	if (!getChildDevice(selectedLPi))
		return;		// this server's corresponding child device hasn't been created yet, so skip this update
	def bridgeMap = jsonMsgBody?.Bridges;
	if (bridgeMap != null && (bridgeMap.size() > 0)) {
		log.debug "LutronPi [${jsonMsgBody.Version}] bridge summary: " + bridgeMap
		getChildDevice(selectedLPi).bridgeUpdate(jsonMsgBody)
		def bridgeUpdatedList = []
		def bridgeGoneOfflineList = []
		// acquire & save the server's bridge info, including a hash to compare for indication of Lutron update
		def aLutronPi = lutronPiMap[selectedLPi]
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
private discoverLutronDevices() {
	log.debug "Discovering your Lutron Devices"

   // Get switches and picos and add to state
	sendToLutronPi('GET','/devices', [:], lutronDeviceHandler)
}

String dniLDevice(String bridgeRef, String deviceRef) {
	if (bridgeRef)
		return (bridgeRef +'.' + deviceRef)
	else
		return deviceRef
}

String dniLScene(String bridgeRef, String sceneRef) {
	if (bridgeRef)
		return (bridgeRef + '.' + sceneRef)
	else
		return 'Lutron' + '.' + sceneRef
}

// Handle device list request response from LutronPi server
def lutronDeviceHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "at the Lutron device list handler"

	def body = hubResponse.json
	if (body != null) {
		log.debug body
		def allSwitches = switches
		allSwitches.clear()
		def allPicos = picos
		allPicos.clear()
		def allShades = shades
		allShades.clear()

		body.each { k ->
			log.debug k
			def lBridge =  k.Bridge ? k.Bridge.toString() : ''
			def dni = dniLDevice(lBridge, k.SerialNumber.toString())
			def lRoom = ((k.FullyQualifiedName?.size() > 1) && (k.FullyQualifiedName[1] == k.Name)) ? k.FullyQualifiedName[0] : ''
			def lipID = k.ID ?: 0;
			def deviceSpec = [:]
			switch (k.DeviceType) {
				case ['WallDimmer','PlugInDimmer','WallSwitch']:
					def zone = k.Zone ?: (k.LocalZones ? (k.LocalZones[0].href?.replaceFirst(/(?i)\/zone\//,'') ?: 0) : 0)
					allSwitches[dni] = [name: k.Name, lipID: lipID, zone: zone, dni: dni, deviceType: k.DeviceType]
					if (lRoom)
						allSwitches[dni] << [lRoom: lRoom]
					break
				case { it.startsWith('Pico') }:		// "Pico2Button*" || "Pico3Button*" || "Pico4Button*" etc.
					allPicos[dni] = [name: k.Name, lipID: lipID, dni: dni, deviceType: k.DeviceType]
					if (lRoom)
						allPicos[dni] << [lRoom: lRoom]
					break;
				case { it.endsWith('Shade') }:		//['SerenaHoneycombShade','SerenaRollerShade','TriathlonHoneycombShade','TriathlonRollerShade','QsWirelessShade']
					def zone = k.Zone ?: (k.LocalZones ? (k.LocalZones[0].href?.replaceFirst(/(?i)\/zone\//,'') ?: 0) : 0)
					allShades[dni] = [name: k.Name, lipID: lipID, zone: zone, dni: dni, deviceType: k.DeviceType]
					if (lRoom)
						allShades[dni] << [lRoom: lRoom]
					break;
			}
		}
	}
}

private discoverLutronScenes() {
	log.debug "Discovering your Lutron Scenes"

	// Get scenes and add to state
	sendToLutronPi('GET','/scenes', [:], lutronSceneHandler)
}

// Handle scene list request response from LutronPi server
def lutronSceneHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "at the Lutron scene list handler"
	def body = hubResponse.json
	if (body != null) {
		log.debug body
		def allScenes = scenes
		allScenes.clear()

		body.each { k ->
			def virtButtonNum = k.href.replaceFirst(/(?i)\/virtualbutton\//,'')
			def lBridge =  k.Bridge ? k.Bridge.toString() : ''
			def dni = dniLScene(lBridge, virtButtonNum.toString())
			allScenes[dni] = [name: k.Name, virtualButton: virtButtonNum, dni: dni]
		}
		log.debug allScenes
	}
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
	log.info "The ${app.name} SmartApp has been uninstalled"
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

	// consolidate selected device list and clean up those previously selected but no longer present
	// note that preference inputs (settings map) is read-only, so selectedXXX lists cannot be programmatically modified
	def selectedDevices = [selectedLPi]
	def deleteDNIList
	def someDevices
	if (selectedSwitches != null) {
		someDevices = switches
		deleteDNIList = []
		selectedDevices += selectedSwitches.findAll {k -> if (someDevices[k]) true; else {deleteDNIList << k; false} }
		deleteDNIList.each {k -> if (['Lutron Dimmer','Lutron Switch'].contains(getChildDevice(k)?.typeName)) deleteChildDevice(k) }
	}
	if (selectedShades != null) {
		someDevices = shades
		deleteDNIList = []
		selectedDevices += selectedShades.findAll {k -> if (someDevices[k]) true; else {deleteDNIList << k; false} }
		deleteDNIList.each {k -> if (getChildDevice(k)?.typeName?.startsWith('Lutron Shade')) deleteChildDevice(k) }
	}
	if (selectedPicos != null) {
		someDevices = picos
		deleteDNIList = []
		selectedDevices += selectedPicos.findAll {k -> if (someDevices[k]) true; else {deleteDNIList << k; false} }
		deleteDNIList.each {k -> if (getChildDevice(k)?.typeName?.startWith('Lutron Pico')) deleteChildDevice(k) }
	}
	if (selectedScenes != null) {
		someDevices = scenes
		deleteDNIList = []
		selectedDevices += selectedScenes.findAll {k -> if (someDevices[k]) true; else {deleteDNIList << k; false} }
		deleteDNIList.each {k -> if (getChildDevice(k)?.typeName?.startWith('Lutron Scene')) deleteChildDevice(k) }
	}

	log.debug "The selected devices are: " + selectedDevices + ". Any other devices will be ignored or deleted"
//	log.debug "Currently installed devices are: " + getAllChildDevices()
	def deleteDevices = (selectedDevices) ? (getAllChildDevices().findAll {!selectedDevices.contains(it.deviceNetworkId)})
	                                      : getAllChildDevices()
	if (deleteDevices) {
		log.debug "Devices that will be deleted are: " + deleteDevices
		deleteDevices.each { deleteChildDevice(it.deviceNetworkId) }
	}
	// If a LutronPi server was actually selected add the child devices
	if (selectedLPi) {
		addLutronPi()
		addDevices('switches')
		addDevices('shades')
		addPicos()
		addScenes()
	}

	ssdpSubscribe()
	ssdpSchedule()
}

String lutronPiLabel(dispIP = null, dispPort = null) {
	'LutronPi Server'
}

def addLutronPi() {
	def dni = selectedLPi
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
			log.info "Adding ${aLutronPiLabel} server: DNI ${dni}"
			aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, dni, aLutronPi.hub, [
						"label": aLutronPiLabel,
						"data": [
								'ip': aLutronPi.ip,
								'port': aLutronPi.port,
								'ssdpUSN': aLutronPi.ssdpUSN,
								'ssdpPath': aLutronPi.ssdpPath
								]
			])
			pollLutronPi()	// do an initial server summary poll; let the SSDP handler take it from here
		} catch (e) {
			log.warn "Unable to add LutronPi device as ${deviceSpec.nameSpace}:${deviceSpec.deviceType}:${dni}",e
		}
	}
}

def addDevices(String devgroup) {
	def dName
	def lRoom
	def zone
	def lipID
	def deviceType
	def dni
	def aChildDevice
	def deviceData

	def allDevices
	def selectedDevices
	switch (devgroup) {
		case 'switches':
			allDevices = switches
			selectedDevices = selectedSwitches.findAll { k-> allDevices[k]}
			break
		case 'shades':
			allDevices = shades
			selectedDevices = selectedShades.findAll { k-> allDevices[k]}
			break
		default:
			return
	}

	selectedDevices?.each { k ->
		log.debug "Selected $devgroup device: " + allDevices[k]

		lRoom = allDevices[k].lRoom ?: ''
		dName = roomifyLutronDeviceName(allDevices[k].lRoom ?: '', allDevices[k].name)
		zone = allDevices[k].zone
		lipID = allDevices[k].lipID
		deviceType = allDevices[k].deviceType
		dni = allDevices[k].dni

		// Check if child device already exists
		aChildDevice = getChildDevice(dni)
		deviceData = [
						'zone': zone,
						'lipID': lipID,
						'deviceType': deviceType,
		             ]
		if (lRoom)
			deviceData << ['lRoom': lRoom]

		if (aChildDevice) {	// just update device data, e.g. name or ID changes
			if (!lockSTDeviceNames)
				aChildDevice.label = dName
			deviceData.each { kk, vv -> aChildDevice.data[kk] = vv }
		} else {			// create a new device
			def deviceSpec = [:]
			switch (deviceType) {
				case ['WallDimmer','PlugInDimmer']:
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Dimmer']
					break;
				case ['WallSwitch']:
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Switch']
					break;
				case { it.endsWith('Shade') }:		//['SerenaHoneycombShade','SerenaRollerShade','TriathlonHoneycombShade','TriathlonRollerShade','QsWirelessShade']
					deviceSpec = [nameSpace:'lutronpi',deviceType:'Lutron Shade']
					break;
			}
			if (deviceSpec) {
				try {
					log.info("Adding ${dName} (${deviceType}) as lipID ${lipID}/Zone ${zone} with DNI ${dni}")
					aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, dni, null, [
						'label': dName,
						'data': deviceData
					])
				} catch (e) {
					log.warn "Unable to add ${dName} as ${deviceSpec.nameSpace}:${deviceSpec.deviceType}:${dni} [ERR=$e]"
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
	def dni
	def aChildDevice
	def deviceData

	def allPicos = picos
	def	selectedDevices = selectedPicos.findAll { k-> allPicos[k]}

	selectedDevices?.each { k ->
		log.debug "Selected Pico: " + allPicos[k]

		lRoom = allPicos[k].lRoom ?: ''
		dName = roomifyLutronDeviceName(allPicos[k].lRoom ?: '', allPicos[k].name)
		lipID = allPicos[k].lipID
		deviceType = allPicos[k].deviceType
		dni = allPicos[k].dni

		// Check if child already exists
		aChildDevice = getChildDevice(dni)
		deviceData = [
						'lipID': lipID,
						'deviceType': deviceType,
		             ]
		if (lRoom)
			deviceData << ['lRoom': lRoom]

		if (aChildDevice) {	// just update device data, e.g. name or ID changes
			if (!lockSTDeviceNames)
				aChildDevice.label = dName
			deviceData.each { kk, vv -> aChildDevice.data[kk] = vv }
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
			}
			if (deviceSpec) {
				try {
					log.info "Adding ${dName} (${deviceType}) with lipID ${lipID} with DNI ${dni}"
					aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, dni, null, [
							'label': dName,
							'data': deviceData
					])
				} catch (e) {
					log.warn "Unable to add ${dName} as ${deviceSpec.nameSpace}:${deviceSpec.deviceType}:${dni} [ERR=$e]"
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
	def dni
	def aChildDevice
	def deviceData

	def allScenes = scenes
	def	selectedDevices = selectedScenes.findAll { k-> allScenes[k]}

	selectedDevices?.each { k ->
		log.debug "Selected scene: " + allScenes[k]
		dName = allScenes[k].name
		virtButton = allScenes[k].virtualButton
 		dni = allScenes[k].dni

		// Check if child already exists
		aChildDevice = getChildDevice(dni)
		deviceData = [
					'virtualButton': virtButton,
		             ]

		if (aChildDevice) {	// just update device data, e.g. name changes
			if (!lockSTDeviceNames)
				aChildDevice.label = dName
			deviceData.each { kk, vv -> aChildDevice.data[kk] = vv }
		} else {			// create a new device
			def deviceSpec = [nameSpace: 'lutronpi', deviceType: 'Lutron Scene']
			try {
				log.info "Adding the scene ${dName} with the DNI ${dni}"
				aChildDevice = addChildDevice(deviceSpec.nameSpace, deviceSpec.deviceType, dni, null, [
					'label': dName,
					'data': deviceData
				])
				// no initial refresh required since we cannot keep track of Lutron scene status
			} catch (e) {
				log.warn "Unable to add scene ${dName} as ${deviceSpec.nameSpace}:${deviceSpec.deviceType}:${dni} [ERR=$e]"
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
						((d.getDataValue('zone') as Integer) == zone) && (d.device.deviceNetworkId.tokenize('.')[0] == lBridge)
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
			if (lDeviceSN) {
				if (lBridge == lDeviceSN) {	// if bridge == device, it's a scene
					isScene = true;
					dni = dniLScene(lBridge, button as String)
				}
				else					// else it's a Pico
					dni = dniLDevice(lBridge, lDeviceSN)
				aChildDevice = getChildDevice(dni)
			} else {					// lacking a device SN, try looking up the device by LIP ID
				def lipID
				try { lipID = (description?.Body?.ID ?: 0) as Integer } catch (e) { lipID = 0 }
				if (lipID == 1) {		// if ID=1, that's a bridge, so it's a scene
					isScene = true;
					dni = dniLScene(lBridge, button as String)
					aChildDevice = getChildDevice(dni)
				} else
					aChildDevice =
						getAllChildDevices().find { d ->
							((d.getDataValue('lipID') as Integer) == lipID) && (d.device.deviceNetworkId.tokenize('.')[0] == lBridge)
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
			log.warn "LutronPi referenced unknown ${isScene?'scene':'Pico'}: $lBridge:$lDeviceSN:$button"
			break
		default:
			log.warning "Unknown communique from LutronPi: $description"
			break
	}
}

// Send refresh request to LutronPi to get requested dimmer/switch (zone) status
def refresh(zoneDevice) {
	def zBridge = zoneDevice.device.deviceNetworkId.tokenize('.')[0]

	log.debug "refresh by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST','/status',[bridge:zBridge, zone:(zoneDevice.getDataValue('zone')?:0)])
}

// Send request to turn zone on (on assumes level of 100)
def on(zoneDevice, rate = null) {
	def zBridge = zoneDevice.device.deviceNetworkId.tokenize('.')[0]

	log.debug "ON by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST','/on',[bridge:zBridge, zone:(zoneDevice.getDataValue('zone')?:0), fadeSec:(rate?:0)])
}

// Send request to turn zone off (level 0)
def off(zoneDevice, rate = null) {
	setLevel(zoneDevice, 0, rate)
}

// Send request to fade zone raise/lower/stop
def fadeLevel(zoneDevice, fadeCmd) {
	def zBridge = zoneDevice.device.deviceNetworkId.tokenize('.')[0]

	log.debug "fadeLevel $fadeCmd by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST','/setLevel',[bridge:zBridge, zone:(zoneDevice.getDataValue('zone')?:0) as Integer, level:fadeCmd])
}

// Send request to set device to a specific level, optionally with a specified fade time (only works on Pro bridges)
def setLevel(zoneDevice, level, rate = null) {
	def zBridge = zoneDevice.device.deviceNetworkId.tokenize('.')[0]

	log.debug "setLevel by ${zoneDevice.device.getDisplayName()} ${zoneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST','/setLevel',[bridge:zBridge, zone:(zoneDevice.getDataValue('zone')?:0) as Integer, level:level, fadeSec:(rate?:0)])
}

def runScene(sceneDevice) {
	def sceneID = sceneDevice.device.deviceNetworkId.tokenize('.')

	log.debug "Scene ${sceneDevice.device.getDisplayName()} ${sceneDevice.device.deviceNetworkId} sent to LutronPi"
	sendToLutronPi('POST','/scene',[bridge:sceneID[0], virtualButton:sceneID[1] as Integer])
}

def buttonPoke(buttonDevice, buttonButton, action) {
	def buttonID = buttonDevice.device.deviceNetworkId.tokenize('.')

	log.debug "Button ${buttonDevice.device.getDisplayName()} ${buttonDevice.device.deviceNetworkId} (lipID=${buttonDevice.getDataValue("lipID")}) button=$buttonButton action=$action sent to LutronPi"
	sendToLutronPi('POST','/button',[bridge:buttonID[0], deviceSN:buttonID[1], buttonNumber:buttonButton, action:action])
}

def buttonMode(buttonDevice, buttonModeMap) {
	def buttonID = buttonDevice.device.deviceNetworkId.tokenize('.')

	log.debug "Button ${buttonDevice.device.getDisplayName()} ${buttonDevice.device.deviceNetworkId} (lipID=${buttonDevice.getDataValue("lipID")}) buttons=$buttonModeMap sent to LutronPi"
	sendToLutronPi('POST','/buttonmode',[bridge:buttonID[0], deviceSN:buttonID[1],
	                                    pushTime:picoShortPushTime, repeatTime:picoRepeatHoldIntervalTime, buttons: buttonModeMap])
}

def renameChildDevice(aChildDevice, newDeviceName) {
	// placeholder for now
}

private sendToLutronPi(String httpVerb, String httpPath, content = [:], callback = null) {
	if (!httpVerb || !httpPath)
		return

	def aLutronPi = lutronPiMap[selectedLPi]
	def headers = [
		HOST: aLutronPi.ip + ':' + aLutronPi.port,
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
