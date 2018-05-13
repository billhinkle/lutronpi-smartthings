/**
 *  Lutron Pico 3BRL Device Type
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
 *  Based on "Lutron Pico" device handler by Nate Schwartz and its predecessors
 *		/ contributions marked wjh by Bill Hinkle (github: billhinkle) 2018
 *	v1.0.0.00				njschwartz	Initial version: "Lutron Pico" by Nate Schwartz for PJ2-3BRLxxxx Picos
 *	v2.0.0.00	2018-04-22	wjh			Added preferences to set push/hold/repeat vs. press/release modes and 6-second timeout
 *										Added support for push/hold from virtual Pico thing
 *										Added support for routing app (virtual Pico) button presses thru/to Lutron (to trigger Lutron Pico functions too)
 *										Modified device command parse to expect native Lutron button numbers and action values to reduce mapping downstream
 *										Added refresh call to parent buttonMode() with per-button mode and per-Pico-model timeout masking and reporting
 *										Added main screen favorite button for Pico, selectable in preferences
 *										Added Lutron info tile (ID, Room)
 *										Added rename reporting to parent
 */
metadata {
	definition (name: "Lutron Pico 3BRL", namespace: "lutronpi", author: "Bill Hinkle")  {
		capability "Refresh"
		capability "Actuator"
		capability "Button"	// attribute:[button:[action:[pushed,held],data:[buttonNumber]],numberOfButtons:[N]]
		capability "Sensor"
        capability "Contact Sensor"	// attribute:[contact:[action:[closed,open],data:[contactNumber]]]

        command "push1"
        command "push2"
        command "push3"
        command "push4"
        command "push5"
        command "hold1"
        command "hold2"
        command "hold3"
        command "hold4"
        command "hold5"
        command "toggleToPush"
        command "toggleToHold"
        command "toggleHoldLock"
		command "pushFavorite"
        command "reportPicoMode"
      
		attribute "lutronInfo", "string"
		attribute "favorite", "number"
        attribute "holdToggle", "enum", ["Push","Hold"]
        attribute "holdLock", "enum", ["false","true"]
        attribute "button1", "enum", ["Push", "Hold", "Press", "pressed"]
		attribute "button2", "enum", ["Push", "Hold", "Press", "pressed"]
        attribute "button3", "enum", ["Push", "Hold", "Press", "pressed"]
        attribute "button4", "enum", ["Push", "Hold", "Press", "pressed"]
        attribute "button5", "enum", ["Push", "Hold", "Press", "pressed"]
   	}

	simulator {
        status "getMode": "data: {action: getmode}"
		status "Push 1":  "data: {buttonNumber: 1, action: pushed}"
        status "Hold 1":  "data: {buttonNumber: 1, action: held}"
		status "Push 2":  "data: {buttonNumber: 2, action: pushed}"
        status "Hold 2":  "data: {buttonNumber: 2, action: held}"
		status "Push 3":  "data: {buttonNumber: 3, action: pushed}"
        status "Hold 3":  "data: {buttonNumber: 3, action: held}"
		status "Push 4":  "data: {buttonNumber: 4, action: pushed}"
        status "Hold 4":  "data: {buttonNumber: 4, action: held}"
		status "Push 5":  "data: {buttonNumber: 5, action: pushed}"
        status "Hold 5":  "data: {buttonNumber: 5, action: held}"
	}

	tiles (scale: 2) {
        standardTile("mainfavorite", "favorite", width: 6, height: 1, canChangeIcon: true, canChangeBackground: true) {
			state "favorite", label: 'FAVE:[${currentValue}]', defaultState: true, nextState: "><", action: "pushFavorite", icon: "http://i67.tinypic.com/4hujyv.png"
			state "0", label: '', icon: "http://i67.tinypic.com/4hujyv.png"
		}

 		standardTile("btn1", "button1", width: 3, height: 2, decoration: "flat") {
			state "Push", label: '${name} 1', icon: "st.Lighting.light11", backgroundColor: "#ffffff", action: "push1"
            state "Hold", label: '${name} 1', icon: "st.Lighting.light11", backgroundColor: "#ffffff", nextState: "Hold", action: "hold1"
			state "Press", label: '${name} 1', icon: "st.Lighting.light11", backgroundColor: "#ffffff", nextState: "pressed", action: "push1"
			state "pressed", label: '1', icon: "st.contact.contact.closed", backgroundColor: "#ffff00", nextState: "Press", action: "hold1"
		}
 		standardTile("btn2", "button2", width: 3, height: 2, decoration: "flat") {
			state "Push", label: '${name} 2', icon: "st.Lighting.light13", backgroundColor: "#ffffff", action: "push2"
			state "Hold", label: '${name} 2', icon: "st.Lighting.light13", backgroundColor: "#ffffff", nextState: "Hold", action: "hold2"
			state "Press", label: '${name} 2', icon: "st.Lighting.light13", backgroundColor: "#ffffff", nextState: "pressed", action: "push2"
			state "pressed", label: '2', icon: "st.contact.contact.closed", backgroundColor: "#ffff00", nextState: "Press", action: "hold2"
		}
 		standardTile("btn3", "button3", width: 1, height: 2, decoration: "flat") {
			state "Push", label: '${name} 3', icon: "st.camera.camera", backgroundColor: "#ffffff", action: "push3"
			state "Hold", label: '${name} 3', icon: "st.camera.camera", backgroundColor: "#ffffff", nextState: "Hold", action: "hold3"
            state "Press", label: '${name} 3', icon: "st.camera.camera", backgroundColor: "#ffffff", nextState: "pressed", action: "push3"
			state "pressed", label: '3', icon: "st.contact.contact.closed", backgroundColor: "#ffff00", nextState: "Press", action: "hold3"
		} 		
 		standardTile("btn4", "button4", width: 1, height: 2, decoration: "flat") {
			state "Push", label: '${name} 4', icon: "st.thermostat.thermostat-up", backgroundColor: "#ffffff", action: "push4"
			state "Hold", label: '${name} 4', icon: "st.thermostat.thermostat-up", backgroundColor: "#ffffff",  nextState: "Hold", action: "hold4"            
			state "Press", label: '${name} 4', icon: "st.thermostat.thermostat-up", backgroundColor: "#ffffff", nextState: "pressed", action: "push4"
			state "pressed", label: '4', icon: "st.contact.contact.closed", backgroundColor: "#ffff00", nextState: "Press", action: "hold4"
		}
		standardTile("btn5", "button5", width: 1, height: 2, decoration: "flat") {
			state "Push", label: '${name} 5', icon: "st.thermostat.thermostat-down", backgroundColor: "#ffffff", action: "push5"
			state "Hold", label: '${name} 5', icon: "st.thermostat.thermostat-down", backgroundColor: "#ffffff",  nextState: "Hold", action: "hold5"            
			state "Press", label: '${name} 5', icon: "st.thermostat.thermostat-down", backgroundColor: "#ffffff", nextState: "pressed", action: "push5"
			state "pressed", label: '5', icon: "st.contact.contact.closed", backgroundColor: "#ffff00", nextState: "Press", action: "hold5"
		}

        standardTile("picoIcon", "", width: 3, height: 3, decoration: "flat", canChangeIcon: true) {
			state "default", label:'', icon: "http://i67.tinypic.com/4hujyv.png", backgroundColor: "#ffffff"
		}

       	standardTile("holdKey", "holdToggle", width: 3, height: 2, decoration: "flat") {
				state "Push", label: 'Hold Shift', icon: "", defaultState: true, backgroundColor: "#ffffff", action: "toggleToHold"
				state "Hold", label: 'un-hold', icon: "", backgroundColor: "#808080", action: "toggleToPush"
		}  
       	standardTile("holdLockKey", "holdLock", width: 3, height: 1, decoration: "flat") {
				state "false", label: 'Hold Lock', icon: "st.tesla.tesla-unlocked", defaultState: true, backgroundColor: "#ffffff", action: "toggleHoldLock"
				state "true",  label: '', icon: "st.tesla.tesla-locked", backgroundColor: "#808080", action: "toggleHoldLock"
		}  

		valueTile("lInfo", "lutronInfo", width: 5, height: 1, decoration: "flat") {
			state "lutronInfo", label: '${currentValue}', backgroundColor: "ffffff"
		}

		main "mainfavorite"
		details(["btn1", "picoIcon", "btn4", "btn3", "btn5", "holdKey", "btn2", "holdLockKey", "lInfo"])
	}
    
    preferences {
    	input name: "faveButton", type: "number", title: "Favorite", 
        			defaultValue: 0, range: "0..5", description: "Things Favorite [0=none]", required: false
    	input name: "sendThruLutron", type: "bool", title: "Buttons Thru/To Lutron", 
        			defaultValue: false, description: "App buttons thru/to Lutron", required: false
		input name: "holdTimeout", type: "bool", title: "Hold Timeout", 
        			defaultValue: true, description: "6sec timeout", required: false
		section("Select button modes:") {
		input name: "btn1Mode", type: "enum", title: "Button 1", options: ["Push/Hold", "Push/Repeat", "Press/Release"],
                    defaultValue: "Push/Hold", description: "Button 1 mode", required: false
		input name: "btn2Mode", type: "enum", title: "Button 2", options: ["Push/Hold", "Push/Repeat", "Press/Release"],
                    defaultValue: "Push/Hold", description: "Button 2 mode", required: false
		input name: "btn3Mode", type: "enum", title: "Button 3", options: ["Push/Hold", "Push/Repeat", "Press/Release"],
                    defaultValue: "Push/Hold", description: "Button 3 mode", required: false
		input name: "btn4Mode", type: "enum", title: "Button 4", options: ["Push/Hold", "Push/Repeat", "Press/Release"],
                    defaultValue: "Push/Hold", description: "Button 4 mode", required: false
		input name: "btn5Mode", type: "enum", title: "Button 5", options: ["Push/Hold", "Push/Repeat", "Press/Release"],
                    defaultValue: "Push/Hold", description: "Button 4 mode", required: false
        }
	}
}

def parse(description) {
//	log.debug "${device.label} parses: " + description
	if (!(description instanceof java.util.Map) || !description.containsKey('name') || !description.containsKey('value'))
		return

	def action = description.value
	if (action == 'getmode') {
		reportPicoMode()
		return;
	} else if (['open','closed','pushed','held'].contains(action)) {
		// translate Lutron button numbers to our internal button numbers & fire action
		try {
			def aButton = btnA0PicoToLPico.findIndexOf {it == (description.data.buttonNumber as Integer)} + 1
			if (aButton){
//				log.debug "${device.label} app button # ${aButton} action=${action}"
				buttonEvent(aButton, action)                    
			}
	    } catch (e) {}
	}
}

def buttonEvent(button, action) {
	button = button as Integer
    log.info "Pico ${device.label} button ${button} event: action = ${action}"

	if (settings["btn${button}Mode"] == "Press/Release") {
        def bValue = null
        if (action == 'closed')
        	bValue = 'pressed'
        else if (action == 'open')
        	bValue = 'Press'
		if (bValue)
			sendEvent(name: "button${button}", value: bValue, isStateChange: true)
		if (button == (settings["faveButton"]?:0))
			sendEvent(name: "favorite", value: "${(action == 'closed') ? '><' : button}", isStateChange: true)    
	}

	def eAttrib = (["open","closed"].contains(action)) ? "contact" : "button"
	createEvent(name: eAttrib, value: action, data: ((eAttrib == 'contact')?[contactNumber: button]:[buttonNumber: button]),
				descriptionText: "${device.label} $eAttrib $button was $action", isStateChange: true)
}

Boolean toggleToPush() {
    def btnMode = btnModeList
    1.upto(btnMode.size()-1,{b ->
		if (btnMode[b] != "Press/Release") {
			sendEvent(name: "button${b}", value: "Push")
		}
    })
	sendEvent(name: "holdToggle", value: "Push", isStateChange: true)
	sendEvent(name: "holdLock", value: "false", isStateChange: true)
    true
}

Boolean toggleToHold() {
	Boolean anyHoldable = false;
    def btnMode = btnModeList
    1.upto(btnMode.size()-1,{b ->
		if (btnMode[b] != "Press/Release") {
			sendEvent(name: "button${b}", value: "Hold")
            anyHoldable = true;
		}
    })
    sendEvent(name: "holdToggle", value: (anyHoldable?"Hold":"Push"), isStateChange: true)
	anyHoldable
}

def toggleHoldLock() {
	if (device.currentValue("holdLock") == "true")
    	toggleToPush()
	else
		sendEvent(name: "holdLock", value: ((device.currentValue("holdToggle") == "Hold") || toggleToHold()), isStateChange: true)
}

def push1() {
	push(1)
}
def push2() {
	push(2)
}
def push3() {
	push(3)
}
def push4() {
	push(4)
}
def push5() {
	push(5)
}

def hold1() {
	hold(1)
}
def hold2() {
	hold(2)
}
def hold3() {
	hold(3)
}
def hold4() {
	hold(4)
}
def hold5() {
	hold(5)
}

def pushFavorite() {
	def iFavorite = settings["faveButton"]?:0
    if (iFavorite >= 1 && iFavorite <= device.currentValue("numberOfButtons")) {
	    def vFavorite = device.currentValue("button${iFavorite}")
        if (vFavorite == "Push" || vFavorite == "Press")
			push(iFavorite)
		else if (vFavorite == "Hold" || vFavorite == "pressed")
			hold(iFavorite) 
	}
	if (settings["btn${iFavorite}Mode"] != "Press/Release")    
		sendEvent(name: "favorite", value: iFavorite, isStateChange: true)
}

def push(button) {
	def eAttrib
	def eValue
    def eData
	if (settings["btn${button}Mode"] == "Press/Release") {
		eAttrib = "contact"
        eValue = "closed"
        eData = [contactNumber: button]
		sendEvent(name: "button${button}", value: "pressed", isStateChange: true)
   		if (button == (settings["faveButton"]?:0))
			sendEvent(name: "favorite", value: '><', isStateChange: true)
	}
	else {
		eAttrib = "button"
		eValue = "pushed"
        eData = [buttonNumber: button]
    }
    log.info "Pico ${device.label} button $button event: action = $eValue"
   	if (sendThruLutron)
		picoSendAction(button,eValue)
	else
		sendEvent(name: eAttrib, value: eValue, data: eData, descriptionText: "${device.label} $eAttrib $button was $eValue", isStateChange: true)
}

def hold(button) {
	def eAttrib
	def eValue
    def eData
	if (settings["btn${button}Mode"] == "Press/Release") {
		eAttrib = "contact"
        eValue = "open"
		eData = [contactNumber: button]
		sendEvent(name: "button${button}", value: "Press", isStateChange: true)
   		if (button == (settings["faveButton"]?:0))
			sendEvent(name: "favorite", value: button, isStateChange: true)
	}
	else {
		eAttrib = "button"
		eValue = "held"
		eData = [buttonNumber: button]
		if (device.currentValue("holdLock") == "false") {
			toggleToPush()
		}   
    }
    log.info "Pico ${device.label} button $button event: action = $eValue"
   	if (sendThruLutron)
		picoSendAction(button,eValue)
	else
		sendEvent(name: eAttrib, value: eValue, data: eData, descriptionText: "${device.label} $eAttrib $button was $eValue", isStateChange: true)
}

def picoSendAction(button, action) {
	def bLPico = btnA0PicoToLPico[(button as Integer) - 1]
    if (bLPico)
		parent.buttonPoke(this, bLPico, action)
}

def reportPicoMode() {

    def timeoutMask = [	Pico3ButtonRaiseLower:		[true,true,true,false,false]
                      ]

    def holdTimeout = settings['holdTimeout']?:(metadata['preferences']['sections']['input'][0].find{p -> (p.name == 'holdTimeout')}?.defaultValue?:false)
	def toMask = timeoutMask[getDataValue('deviceType')] ?: [true,true,true,false,false];
	toMask.eachWithIndex {m, i -> toMask[i] = m && holdTimeout}

	def btnMode = btnModeList.drop(1)
    def modeMap = [:]
	btnA0PicoToLPico.eachWithIndex {l, i -> modeMap[l] = [mode : btnMode[i], to6Sec : toMask[i]] }
    
    // data format: [2:[mode:mode2, to6Sec:true/false], 4:[mode:mode4, to6Sec:true/false], 3:[mode:mode3, to6Sec:true/false], 5:[mode:mode5, to6Sec:true/false], 6:[mode:mode6, to6Sec:true/false]]
    // where modeN = ["Push/Hold", "Push/Repeat", "Press/Release"] e.g.
    //       metadata["preferences"]["sections"]["input"][1].find{p -> (p.name == "btn${1}Mode")}?.options
	// log.debug "Pico mode options=${metadata["preferences"]["sections"]["input"][1].find{p -> (p.name == "btn${1}Mode")}?.options}"
	parent.buttonMode(this, modeMap);
}

// our app button (-1) to pico button code
def getBtnA0PicoToLPico() {
	[2,4,3,5,6]
}

def getBtnModeList() {
	def nButtons = device.currentValue("numberOfButtons")
    def btnMode = []
	1.upto(nButtons,{b ->
    	btnMode[b] = settings["btn${b}Mode"]?:(metadata["preferences"]["sections"]["input"][1].find{p -> (p.name == "btn${b}Mode")}?.defaultValue?:"Push/Hold")
	})
	btnMode
}

def refresh() {
	reportPicoMode()
}

def installed() {
	state.label = device.label

	def N_BUTTONS = btnA0PicoToLPico.size()
	sendEvent(name: "numberOfButtons", value: N_BUTTONS)
    
	log.info "Pico ${device.label} installed: ${N_BUTTONS} buttons"

	initialize()
}

def updated() {
	log.info "Pico ${device.label} prefs updated"
	initialize()
}

def initialize() {
   if (state.label != device.label) {
		log.info "Pico ${state.label} renamed to ${device.label}"
		state.label = device.label
		parent.renameChildDevice(this, device.label);
    }

	def nButtons = device.currentValue("numberOfButtons")
    sendEvent(name: "favorite", value: (settings["faveButton"]?:(metadata["preferences"]["sections"]["input"][0].find{p -> (p.name == "faveButton")}?.defaultValue?:0)))

    refresh()	// refresh parent with current button modes

	sendEvent (name: "lutronInfo", value: "Lutron ${device.deviceNetworkId.tokenize('.')[0]} devID:${device.getDataValue('lipID')}\n[${device.getDataValue('lRoom')}]")

	// update the device tiles for current button modes
    sendEvent(name: "holdLock", value: false)
	sendEvent(name: "holdToggle", value: "Push")
	def btnMode = btnModeList
	1.upto(nButtons,{b ->
		sendEvent(name: "contact", value: "open", data: [contactNumber: b])
		if (btnMode[b] == "Press/Release") {
			sendEvent(name: "button${b}", value: "Press")
		} else {
			sendEvent(name: "button${b}", value: "Push")
		}
	})
}
