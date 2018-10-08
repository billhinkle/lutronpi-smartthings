/**
 *  Lutron Keypad (non-Pico) Device Type
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Based on "Lutron Pico 4B" device handler by Bill Hinkle/Nate Schwartz and its predecessors
 *		/ contributions marked wjh by Bill Hinkle (github: billhinkle) 2018
 *	v1.0.0.00	2018-10-05	wjh			Initial version: generic support for non-Pico Lutron keypads
 */
metadata {
	definition (name: "Lutron Keypad", namespace: "lutronpi", author: "Bill Hinkle")  {
		capability "Refresh"
		capability "Actuator"
		capability "Button"	// attribute:[button:[action:[pushed,held],data:[buttonNumber]],numberOfButtons:[N]]
		capability "Sensor"
        capability "Contact Sensor"	// attribute:[contact:[action:[closed,open],data:[contactNumber]]]

        command "pushBtn"
		command "holdBtn"
		command "nextBtn"
		command "prevBtn"
        command "toggleToPush"
        command "toggleToHold"
        command "toggleHoldLock"
		command "pushFavorite"
        command "reportKeypadMode"
      
		attribute "lutronInfo", "string"
		attribute "favorite", "number"
        attribute "holdToggle", "enum", ["Push","Hold"]
        attribute "holdLock", "enum", ["false","true"]
        attribute "buttonNumber", "number"
        attribute "buttonOp", "enum", ["Push", "Hold", "Press", "pressed"]
   	}

	simulator {
	}

	tiles (scale: 2) {
        standardTile("mainfavorite", "favorite", width: 6, height: 1, canChangeIcon: true, canChangeBackground: true) {
			state "favorite", label: 'FAVE:[${currentValue}]', defaultState: true, nextState: "><", action: "pushFavorite", icon: "st.unknown.zwave.remote-controller"
			state "0", label: '', icon: "st.unknown.zwave.remote-controller"
		}

        multiAttributeTile(name:"kpButton", type: "generic", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true){
            tileAttribute ("buttonOp", key: "PRIMARY_CONTROL") {
                attributeState "Push", label:'${name}', icon:"", backgroundColor:"#ffffff", action: "pushBtn"
                attributeState "Hold", label:'${name}', icon:"", backgroundColor:"#ffffff", nextState: "Hold", action: "holdBtn"
                attributeState "Press", label:'${name}', icon:"", backgroundColor:"#ffffff", nextState: "pressed", action: "pushBtn"
                attributeState "pressed", label:'', icon:"st.contact.contact.closed", backgroundColor:"#ffff00", nextState: "Press", action: "holdBtn"
			}
			tileAttribute("buttonNumber", key: "VALUE_CONTROL") {
				attributeState "VALUE_UP", label: '${currentValue}', action: "nextBtn"
				attributeState "VALUE_DOWN", label: '${currentValue}', action: "prevBtn"
			}
		}
        standardTile("keypadIcon", "", width: 1, height: 1, decoration: "flat", canChangeIcon: true) {
			state "default", label:'', icon: "st.unknown.zwave.remote-controller", backgroundColor: "#ffffff"
		}
		valueTile("kpButtonSelect", "buttonNumber", width: 2, height: 1, decoration: "flat") {
			state "buttonNumber", label: '${currentValue}', backgroundColor: "ffffff"
		}
		standardTile("holdKey", "holdToggle", width: 2, height: 1, decoration: "flat") {
				state "Push", label: 'Hold Shift', icon: "", defaultState: true, backgroundColor: "#ffffff", action: "toggleToHold"
				state "Hold", label: 'un-hold', icon: "", backgroundColor: "#808080", action: "toggleToPush"
		}  
       	standardTile("holdLockKey", "holdLock", width: 1, height: 1, decoration: "flat") {
				state "false", label: 'Hold Lock', icon: "st.tesla.tesla-unlocked", defaultState: true, backgroundColor: "#ffffff", action: "toggleHoldLock"
				state "true",  label: '', icon: "st.tesla.tesla-locked", backgroundColor: "#808080", action: "toggleHoldLock"
		}  

		valueTile("lInfo", "lutronInfo", width: 5, height: 1, decoration: "flat") {
			state "lutronInfo", label: '${currentValue}', backgroundColor: "ffffff"
		}
        standardTile("favorite", "favorite", width: 1, height: 1) {
			state "favorite", label: 'FAVE:[${currentValue}]', defaultState: true, nextState: "><", action: "pushFavorite", icon: "st.samsung.da.oven_ic_most_used"
			state "0", label: '', icon: ""
		}

		main "mainfavorite"
		details(["kpButton", "keypadIcon", "kpButtonSelect", "holdKey", "holdLockKey", "lInfo", "favorite"])
	}
    
    preferences {
    	input name: "faveButton", type: "number", title: "Favorite", 
        			defaultValue: 0, range: "0..*", description: "Things Favorite [0=none]", required: false
    	input name: "sendThruLutron", type: "bool", title: "Buttons Thru/To Lutron", 
        			defaultValue: true, description: "App buttons thru/to Lutron", required: false
		input name: "holdTimeout", type: "bool", title: "Hold Timeout", 
        			defaultValue: false, description: "6sec timeout", required: false
		input name: "btnMode", type: "enum", title: "Buttons Mode", options: ["Push/Hold", "Push/Repeat", "Press/Release"],
                    defaultValue: "Push/Hold", description: "All buttons mode", required: false
	}
}

def parse(description) {
//	log.debug "${device.label} parses: " + description
	if (!(description instanceof java.util.Map) || !description.containsKey('name') || !description.containsKey('value'))
		return

	def action = description.value
	if (action == 'getmode') {
		reportKeypadMode()
		return;
	} else if (['open','closed','pushed','held'].contains(action)) {
		// (for now...) Lutron button numbers are the same as our app internal button numbers: fire action
		try {
			def aButton = description.data.buttonNumber as Integer
			if (aButton){
//				log.debug "${device.label} app button # ${aButton} action=${action}"
				buttonEvent(aButton, action)
			}
	    } catch (e) {}
	}
}

private def buttonEvent(button, action) {
	button = button as Integer
    log.info "Keypad ${device.label} button ${button} event: action = ${action}"

	sendEvent(name: "buttonNumber", value: button, isStateChange: true)
	if (settings["btnMode"] == 'Press/Release') {
        def bValue = null
        if (action == 'closed')
        	bValue = 'pressed'
        else if (action == 'open')
        	bValue = 'Press'
		if (bValue)
			sendEvent(name: "buttonOp", value: bValue, isStateChange: true)
		if (button == (settings["faveButton"]?:0))
			sendEvent(name: 'favorite', value: "${(action == 'closed') ? '><' : button}", isStateChange: true)    
	}

	def eAttrib = (['open','closed'].contains(action)) ? 'contact' : 'button'
	createEvent(name: eAttrib, value: action, data: ((eAttrib == 'contact')?[contactNumber: button]:[buttonNumber: button]),
    			descriptionText: "${device.label} $eAttrib $button was $action", isStateChange: true)
}

private Boolean toggleToPush() {
	if (btnModeSetting != 'Press/Release') {
		sendEvent(name: "buttonOp", value: 'Push')
	}
	sendEvent(name: 'holdToggle', value: 'Push', isStateChange: true)
	sendEvent(name: 'holdLock', value: 'false', isStateChange: true)
    true
}

private Boolean toggleToHold() {
	Boolean anyHoldable = false;
	if (btnModeSetting != 'Press/Release') {
		sendEvent(name: "buttonOp", value: 'Hold')
		anyHoldable = true;
	}
    sendEvent(name: 'holdToggle', value: (anyHoldable?'Hold':'Push'), isStateChange: true)
	anyHoldable
}

private def toggleHoldLock() {
	if (device.currentValue('holdLock') == 'true')
    	toggleToPush()
	else
		sendEvent(name: 'holdLock', value: ((device.currentValue('holdToggle') == 'Hold') || toggleToHold()), isStateChange: true)
}

private def pushFavorite() {
	def iFavorite = settings['faveButton']?:0
    if (iFavorite >= 1 && iFavorite <= device.currentValue('numberOfButtons')) {
	    def vFavorite = device.currentValue("buttonOp")
        if (vFavorite == 'Push' || vFavorite == 'Press')
			push(iFavorite)
		else if (vFavorite == 'Hold' || vFavorite == 'pressed')
			hold(iFavorite) 
	}
	if (settings["btnMode"] != 'Press/Release')    
		sendEvent(name: 'favorite', value: iFavorite, isStateChange: true)
}

def pushBtn() {
	def btnNum = device.currentValue('buttonNumber')?:0
	if (btnNum)
		push(btnNum)
}

def holdBtn() {
	def btnNum = device.currentValue('buttonNumber')?:0
	if (btnNum)
		hold(btnNum)
}

def nextBtn() {
	def btnNum = device.currentValue('buttonNumber')?:0
    def btnMax = device.currentValue('numberOfButtons')
	if (btnNum >= btnMax)
		btnNum = btnMax
	else
		++btnNum
	sendEvent(name: "buttonNumber", value: btnNum)
}

def prevBtn() {
	def btnNum = device.currentValue('buttonNumber')?:0
	if (--btnNum < 1)
		btnNum = 1
	sendEvent(name: "buttonNumber", value: btnNum)
}

def push(button) {
	def eAttrib
	def eValue
    def eData

	if (settings["btnMode"] == 'Press/Release') {
		eAttrib = 'contact'
        eValue = 'closed'
        eData = [contactNumber: button]
		sendEvent(name: "buttonOp", value: 'pressed', isStateChange: true)
   		if (button == (settings['faveButton']?:0))
			sendEvent(name: 'favorite', value: '><', isStateChange: true)
	}
	else {
		eAttrib = 'button'
		eValue = 'pushed'
        eData = [buttonNumber: button]
    }
    log.info "Keypad ${device.label} button $button event: action = $eValue"
   	if (sendThruLutronSetting)
		keypadSendAction(button,eValue)
	else
		sendEvent(name: eAttrib, value: eValue, data: eData, descriptionText: "${device.label} $eAttrib $button was $eValue", isStateChange: true)
}

def hold(button) {
	def eAttrib
	def eValue
    def eData

    if (settings["btnMode"] == 'Press/Release') {
		eAttrib = 'contact'
        eValue = 'open'
		eData = [contactNumber: button]
		sendEvent(name: "buttonOp", value: 'Press', isStateChange: true)
   		if (button == (settings['faveButton']?:0))
			sendEvent(name: 'favorite', value: button, isStateChange: true)
	}
	else {
		eAttrib = 'button'
		eValue = 'held'
		eData = [buttonNumber: button]
		if (device.currentValue('holdLock') == 'false') {
			toggleToPush()
		}   
    }
    log.info "Keypad ${device.label} button $button event: action = $eValue"
   	if (sendThruLutronSetting)
		keypadSendAction(button,eValue)
	else
		sendEvent(name: eAttrib, value: eValue, data: eData, descriptionText: "${device.label} $eAttrib $button was $eValue", isStateChange: true)
}

private def keypadSendAction(button, action) {
	def bLKeypad = button as Integer
    if (bLKeypad)
		parent.buttonPoke(this, bLKeypad, action)
}

private def reportKeypadMode() {

    def holdTimeout = settings['holdTimeout']?:(metadata['preferences']['sections']['input'][0].find{p -> (p.name == 'holdTimeout')}?.defaultValue?:false)
	def btnMode = btnModeSetting
	def modeMap = [0:[mode : btnMode, to6Sec : holdTimeout]]
 
    // data format: [0:[mode:modeAll, to6Sec:true/false]]
    // where modeN = ["Push/Hold", "Push/Repeat", "Press/Release"] e.g.
    //       metadata["preferences"]["sections"]["input"][0].find{p -> (p.name == "btnMode")}?.options
	// log.debug "Keypad mode options=${metadata["preferences"]["sections"]["input"][0].find{p -> (p.name == "btnMode")}?.options}"
	parent.buttonMode(this, modeMap);
}

private def getBtnModeSetting() {
	settings['btnMode']?:(metadata['preferences']['sections']['input'][0].find{p -> (p.name == 'btnMode')}?.defaultValue?:'Push/Hold')
}

private def getSendThruLutronSetting() {
	settings['sendThruLutron']?:(metadata['preferences']['sections']['input'][0].find{p -> (p.name == 'sendThruLutron')}?.defaultValue?:'true')
}

def refresh() {
	reportKeypadMode()
}

def installed() {
	state.label = device.label

	def N_BUTTONS = (device.getDataValue('maxButton')?:1) as Integer
	log.info "Keypad ${device.label} installed: $N_BUTTONS buttons"

	initialize()
}

def updated() {
	log.info "Keypad ${device.label} prefs updated"
	initialize()
}

private def initialize() {
   if (state.label != device.label) {
		log.info "Keypad ${state.label} renamed to ${device.label}"
		state.label = device.label
		parent.renameChildDevice(this, device.label);
    }

	def N_BUTTONS = (device.getDataValue('maxButton')?:1) as Integer
	sendEvent(name: 'numberOfButtons', value: N_BUTTONS)
    sendEvent(name: 'favorite',
              value: (settings['faveButton']?:(metadata['preferences']['sections']['input'][0].find{p -> (p.name == 'faveButton')}?.defaultValue?:0)))

    refresh()	// refresh parent with current button modes

	def lRoom = device.getDataValue('lRoom')
    def lInfoText = "Lutron ${device.deviceNetworkId.tokenize('.')[0]} devID:${device.getDataValue('lipID')}" + (lRoom?"\n[$lRoom]":'')
	sendEvent (name: "lutronInfo", value: lInfoText)

	// update the device tiles for current button modes
    sendEvent(name: 'holdLock', value: false)
	sendEvent(name: 'holdToggle', value: 'Push')
	sendEvent(name: 'buttonNumber', value: 0)
	if (btnModeSetting == 'Press/Release') {
		sendEvent(name: "buttonOp", value: 'Press')
	} else {
		sendEvent(name: "buttonOp", value: 'Push')
	}
	// individual contact initialization may lead to a very large burst of unnecessary events and rate limiting
    // 1.upto(N_BUTTONS) { b -> sendEvent(name: 'contact', value: 'open', data: ['contactNumber': b]) }
    sendEvent(name: 'contact', value: 'open')
	sendEvent(name: 'buttonNumber', value: 1)
}
