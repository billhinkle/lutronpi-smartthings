/**
 *	Lutron Dimmer Device Type
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
 *  Based on SmartThings "Lutron Virtual Dimmer" by Nate Schwartz
 *		/ contributions marked wjh by Bill Hinkle (github: billhinkle) 2018
 *	v2.0.0.00	2018-05-10	wjh	Initial version:	Converted to multiattribute tile UI, added Lutron device info,
 *													Added direct fade commands and fade (ramp) enable and timing for on & off
 *													Added 100%-on button, moved refresh out of the way
 *												 	Added rename reporting to parent
 */
 metadata {
        definition (name: "Lutron Dimmer", namespace: "lutronpi", author: "Bill Hinkle") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		capability "Refresh"
		capability "Switch Level"

		command "setLevelUser", ["number"]
		command "setRampOffEnable"
		command "resetRampOffEnable"
		command "setRampOffSeconds", ["number"]
        command "setRampOnEnable"
		command "resetRampOnEnable"
		command "setRampOnSeconds", ["number"]
		command "fullOn"
		command "levelUp"
		command "levelDown"
		command "levelStop"

		attribute "dimControl", "enum", ["off", "on", "\u25BC", "\u25B2", "\u25BD", "\u25B3"] //'off','on','▼', '▲', '▽', '△'
		attribute "fading", "number"
		attribute "rampOffSeconds", "number"
		attribute "rampOffEnable", "enum", ["false", "true"]
		attribute "rampOnSeconds", "number"
		attribute "rampOnEnable", "enum", ["false", "true"]
		attribute "lutronInfo", "string"
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"dimmer", type: "generic", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true){
            tileAttribute ("dimControl", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action: "switch.off", icon:"st.switches.light.on", backgroundColor:"#00a0dc", nextState:"\u25BC" //"▼"
                attributeState "off", label:'${name}', action: "switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"\u25B2" //"▲"
                attributeState "\u25B2", label:'${name}', action: "levelStop", icon:"st.switches.light.on", backgroundColor:"#80c0ff" // "#00a0dc" //"▲"
                attributeState "\u25BC", label:'${name}', action: "levelStop", icon:"st.switches.light.off", backgroundColor:"#c0f0ff" // "#ffffff" //"▼"
				attributeState "\u25B3", label:'STOP ${name}', action: "levelStop", icon:"st.switches.light.on", backgroundColor:"#0000ff" //"△"
				attributeState "\u25BD", label:'STOP ${name}', action: "levelStop", icon:"st.switches.light.off", backgroundColor:"#00007f" //"▽"
            }
			tileAttribute("dimControl", key: "SECONDARY_CONTROL") {
				attributeState "default", label: '', action: "fullOn", icon:"st.Weather.weather14"
			}
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", label: 'Brightness', action: "setLevelUser" //"switch level.setLevel"
            }
			tileAttribute("fading", key: "VALUE_CONTROL") {
				attributeState "VALUE_UP", action: "levelUp"
				attributeState "VALUE_DOWN", action: "levelDown"
			}
		}
		standardTile("rampOffControl", "rampOffEnable", width: 2, height: 2, decoration: "flat") {
			state "false", label: 'No Fade>Off', defaultState: true, icon: "st.motion.acceleration.inactive", backgroundColor: "#ffffff", nextState: "true", action: "setRampOffEnable"
			state "true", label: 'Fade>Off', defaultState: false, icon: "st.motion.acceleration.active", backgroundColor: "#dddddd", nextState: "false", action: "resetRampOffEnable"
		}
		controlTile("rampOffSlider", "rampOffSeconds", "slider", width: 1, height: 2, range:"(0..120)", unit:"sec") {
			state "rampOffSeconds",  label: 'Fade/Off (sec)', action: "setRampOffSeconds"
		}
		standardTile("rampOnControl", "rampOnEnable", width: 2, height: 2, decoration: "flat") {
			state "false", label: 'No Fade>On', defaultState: true, icon: "st.motion.acceleration.inactive", backgroundColor: "#ffffff", nextState: "true", action: "setRampOnEnable"
			state "true", label: 'Fade>On', defaultState: false, icon: "st.motion.acceleration.active", backgroundColor: "#dddddd", nextState: "false", action: "resetRampOnEnable"
		}
		controlTile("rampOnSlider", "rampOnSeconds", "slider", width: 1, height: 2, range:"(0..120)", unit:"sec") {
			state "rampOnSeconds",  label: 'Fade/On (sec)', action: "setRampOnSeconds"
		}
		standardTile("dimmerRefresh", "device.refresh", width: 1, height: 1, decoration: "flat") {
			state "default", label: '', icon:"st.secondary.refresh", action: "refresh.refresh"
		}
		valueTile("lInfo", "lutronInfo", width: 5, height: 1, decoration: "flat") {
			state "lutronInfo", label: '${currentValue}', backgroundColor: "#ffffff"
		}

		main "dimmer"
        details(["dimmer", "rampOffControl", "rampOffSlider", "rampOnControl", "rampOnSlider", "lInfo", "dimmerRefresh"])
    }
}

def parse(description) {
//	log.debug "${device.label} parses: " + description

	if (!(description instanceof java.util.Map) || !description.containsKey('name') || !description.containsKey('value'))
		return

    if (description.name == 'level') {
		def level = description.value?:0 as Integer
		if (level > 0) {
        	if (level > 100)
            	level = 100
	    	sendEvent(name: 'level', value: level)
			sendEvent(name: 'switch', value: 'on')
            sendEvent(name: "dimControl", value: "on")
		} else {
			sendEvent(name: 'switch', value: 'off')
            sendEvent(name: "dimControl", value: "off")
		}
		if (state.fading)
			levelStop()
	}
}

def fullOn() {
	parent.on(this, device.currentValue("rampOnEnable").toBoolean()?device.currentValue("rampOnSeconds"):0)
//	depend on the Lutron's response to update this state, to avoid races
//	sendEvent(name: "switch", value: "on")
	sendEvent(name: 'dimControl', value: '\u25B2')	//"▲"
	log.info "Dimmer ${device.label} On (full)"
}

def on() {
	def level = device.currentValue("level")
    if (level <= 0)
    	level = 100;
	def rate = device.currentValue("rampOnEnable").toBoolean()?device.currentValue("rampOnSeconds"):0;
	parent.setLevel(this, level, rate)
//	depend on the Lutron's response to update these states, to avoid races
//	sendEvent(name: "switch", value: "on")
//	sendEvent(name: "dimControl", value: "on")
	log.info "Dimmer ${device.label} On: ${level}% ${rateLogText(rate)}"
}

def off() {
	parent.off(this, device.currentValue("rampOffEnable").toBoolean()?device.currentValue("rampOffSeconds"):0)
//	depend on the Lutron's response to update these states, to avoid races
//	sendEvent(name: "switch", value: "off")
//	sendEvent(name: "dimControl", value: "off")
	log.info "Dimmer ${device.label} Off"
}

// the default capability setLevel does not use the local ramp (fade) rates... must be specified, else 0/immediate
def setLevel(level, rate = 0) {
 	if (level < 0) level = 0
	else if( level > 100) level = 100

    log.info "Dimmer ${device.label} setLevel: ${level}% ${rateLogText(rate)}"
    parent.setLevel(this, level, rate)
    sendEvent(name: "level", value: level)
}

// the user-triggered setLevel uses the local ramp (fade) rates
def setLevelUser(level) {
 	if (level < 0) level = 0
	else if( level > 100) level = 100

	def rate = 0
	if (level < device.currentValue("level")) {
    	if (device.currentValue("rampOffEnable").toBoolean())
        	rate = device.currentValue("rampOffSeconds")
	} else {
    	if (device.currentValue("rampOnEnable").toBoolean())
        	rate = device.currentValue("rampOnSeconds")
    }
    log.info "Dimmer ${device.label} setLevel: ${level}% ${rateLogText(rate)}"
    parent.setLevel(this, level, rate)
    sendEvent(name: "level", value: level)
}

def levelUp() {
	if (state.fading) {
    	levelStop()
	} else {
		state.fading = true;
		parent.fadeLevel(this, "raise")
	   	sendEvent(name: "dimControl", value: '\u25B3') //'△'
	}
//	setLevel(device.currentValue("level") + 5);
}

def levelDown() {
	if (state.fading) {
    	levelStop()
	} else {
		state.fading = true;
		parent.fadeLevel(this, "lower")
	   	sendEvent(name: "dimControl", value: '\u25BD') //'▽'
    }
//	setLevel(device.currentValue("level") - 5);
}

def levelStop() {
	state.fading = false;
	parent.fadeLevel(this, "stop")
//	depend on the Lutron's response to update this state, to avoid races
//   	sendEvent(name: "dimControl", value: ((currentSwitch == 'on')?'on':'off'))
}

def resetRampOffEnable() {
	sendEvent(name: "rampOffEnable", value: "false")
	log.info "Dimmer ${device.label} setRampOffEnable: ${device.currentValue("rampOffEnable")}"
}

def resetRampOnEnable() {
	sendEvent(name: "rampOnEnable", value: "false")
	log.info "Dimmer ${device.label} setRampOnEnable: ${device.currentValue("rampOnEnable")}"
}

def setRampOffEnable() {
    sendEvent(name: "rampOffEnable", value: "true")
	log.info "Dimmer ${device.label} setRampOffEnable: ${device.currentValue("rampOffEnable")}"
}

def setRampOnEnable() {
    sendEvent(name: "rampOnEnable", value: "true")
	log.info "Dimmer ${device.label} setRampOnEnable: ${device.currentValue("rampOnEnable")}"
}

def setRampOffSeconds(rate) {
    sendEvent(name: "rampOffSeconds", value: rate)
	log.info "Dimmer ${device.label} setRampOffSeconds: ${rate} sec"
}

def setRampOnSeconds(rate) {
    sendEvent(name: "rampOnSeconds", value: rate)
	log.info "Dimmer ${device.label} setRampOnSeconds: ${rate} sec"
}

private String rateLogText(rate) {
	rate?"over ${rate} sec":""
}

def refresh() {
	log.info "Dimmer ${device.label} refresh"
    parent.refresh(this)
}

def installed() {
	state.label = device.label
    log.info "Dimmer ${device.label} installed" 
    
    resetRampOffEnable()
    setRampOffSeconds(0)
    resetRampOnEnable()
	setRampOnSeconds(0)

    initialize()
}

def updated() {
	initialize()
}

def initialize() {
    if (state.label != device.label) {
    	log.info "Dimmer ${state.label} renamed to ${device.label}"
		state.label = device.label
		parent.renameChildDevice(this, device.label);
    }
    levelStop()

	refresh()
    
	sendEvent (name: "lutronInfo", value: "Lutron ${device.deviceNetworkId.tokenize('.')[0]} devID:${device.getDataValue('lipID')} Zone:${device.getDataValue("zone")}\n[${device.getDataValue('lRoom')}]")

}
