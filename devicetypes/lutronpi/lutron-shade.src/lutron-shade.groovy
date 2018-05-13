/**
 *	Lutron Shade Device Type
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
 *	v2.0.0.00	2018-05-10	wjh	Initial version: adapted to shades, 
 *												 converted to multiattribute tile UI, added Lutron device info, added fade commands and ramp timing
 *												 Added rename reporting to parent
 */
 metadata {
        definition (name: "Lutron Shade", namespace: "lutronpi", author: "Bill Hinkle") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		capability "Refresh"
		capability "Window Shade"	// attributes: windowShade = enum: closed/closing/open/opening/partially open/unknown
        							// commands: close(), open(), presetPosition()

		command "fullOpen"
		command "levelUp"
		command "levelDown"
		command "levelStop"

		attribute "shadeControl", "enum", ["off", "on", "\u25BC", "\u25B2", "\u25BD","\u25B3"]	// "▼", "▲", "▽", "△"
        attribute "position", "number"
		attribute "moving", "number"
		attribute "lutronInfo", "string"
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"shade", type: "generic", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true){
            tileAttribute ("shadeControl", key: "PRIMARY_CONTROL") {
                attributeState "up", label:'${name}', action:"window shade.close", icon:"http://oi66.tinypic.com/2h7la13.jpg", backgroundColor:"#a1c1f1", nextState:"\u25BC"	//"▼"
                attributeState "down", label:'${name}', action:"window shade.open", icon:"http://oi65.tinypic.com/i5dg5s.jpg", backgroundColor:"#cccccc", nextState:"\u25B2"	//"▲"
                attributeState "\u25B2", label:'${name}', action:"levelStop", icon:"st.custom.buttons.add-icon", backgroundColor:"#83abe6"	// "▲"
                attributeState "\u25BC", label:'${name}', action:"levelStop", icon:"st.custom.buttons.subtract-icon", backgroundColor:"#bbbbbb"	// "▼"
				attributeState "\u25B3", label:'STOP ${name}', action:"levelStop", icon:"st.custom.buttons.add-icon", backgroundColor:"#7193c6"	// "△"
				attributeState "\u25BD", label:'STOP ${name}', action:"levelStop", icon:"st.custom.buttons.subtract-icon", backgroundColor:"#999999"	// "▽"
            }
			tileAttribute("shadeControl", key: "SECONDARY_CONTROL") {
				attributeState "default", label: '', action: "fullOpen", icon:"st.Weather.weather14"
			}
            tileAttribute ("position", key: "SLIDER_CONTROL") {
                attributeState "position", label: 'Position', action:"window shade.presetPosition"
            }
			tileAttribute("moving", key: "VALUE_CONTROL") {
				attributeState "VALUE_UP", action: "levelUp"
				attributeState "VALUE_DOWN", action: "levelDown"
			}
		}
		standardTile("shadeRefresh", "device.refresh", width: 1, height: 1, decoration: "flat") {
			state "default", label: '', icon:"st.secondary.refresh", action: "refresh.refresh"
		}
		valueTile("lInfo", "lutronInfo", width: 5, height: 1, decoration: "flat") {
			state "lutronInfo", label: '${currentValue}', backgroundColor: "#ffffff"
		}

		main "shade"
        details(["shade", "lInfo", "shadeRefresh"])
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
	    	sendEvent(name: 'position', value: level)
            sendEvent(name: 'switch', value: 'on')
			sendEvent(name: 'windowShade', value: (level == 100)?'open':'partially open')
            sendEvent(name: 'shadeControl', value: 'up')
		} else {
            sendEvent(name: 'switch', value: 'off')
			sendEvent(name: 'windowShade', value: 'closed')
            sendEvent(name: "shadeControl", value: 'down')
		}
		if (state.moving)
			levelStop()
	}
}

def fullOpen() {
	parent.on(this)
//	depend on the Lutron's response to update this state, to avoid races
//	sendEvent(name: 'windowShade', value: 'open')
	sendEvent(name: 'shadeControl', value: '\u25B2')	// "▲"
	log.info "Shade ${device.label} Opening (full)"
}

def open() {
	def level = device.currentValue("position")
    if (level <= 0)
    	level = 100;
	parent.setLevel(this, level)
//	depend on the Lutron's response to update these states, to avoid races
//	sendEvent(name: 'windowShade', value: 'open')
//	sendEvent(name: 'shadeControl', value: 'up')
	log.info "Shade ${device.label} Opening to: ${level}%"
}

def close() {
	parent.off(this)
//	depend on the Lutron's response to update these states, to avoid races
//	sendEvent(name: 'windowShade', value: 'closed')
//	sendEvent(name: 'shadeControl', value: 'down')
	log.info "Shade ${device.label} Closing"
}

def presetPosition(level) {
 	if (level < 0) level = 0
	else if( level > 100) level = 100

    log.info "Shade ${device.label} presetPosition: ${level}%"
    parent.setLevel(this, level)
    sendEvent(name: 'position', value: level)
}

def levelUp() {
	if (state.moving) {
    	levelStop()
	} else {
		state.moving = true;
		parent.fadeLevel(this, "raise")
	   	sendEvent(name: 'shadeControl', value: '\u25B3')	// '△'
	}
//	setLevel(device.currentValue('level') + 5);
}

def levelDown() {
	if (state.moving) {
    	levelStop()
	} else {
		state.moving = true;
		parent.fadeLevel(this, "lower")
	   	sendEvent(name: 'shadeControl', value: '\u25BD')	// '▽'
    }
//	setLevel(device.currentValue('level') - 5);
}

def levelStop() {
	state.moving = false;
	parent.fadeLevel(this, "stop")
//	depend on the Lutron's response to update this state, to avoid races
//   	sendEvent(name: 'shadeControl', value: ((currentWindowShade == 'open')?'up':'down'))
}

def refresh() {
	log.info "Shade ${device.label} refresh"
    parent.refresh(this)
}

def installed() {
	state.label = device.label
    log.info "Shade ${device.label} installed" 
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
    if (state.label != device.label) {
    	log.info "Shade ${state.label} renamed to ${device.label}"
		state.label = device.label
		parent.renameChildDevice(this, device.label);
    }
    levelStop()

	refresh()
    
	sendEvent (name: "lutronInfo", value: "Lutron ${device.deviceNetworkId.tokenize('.')[0]} devID:${device.getDataValue('lipID')} Zone:${device.getDataValue("zone")}\n[${device.getDataValue('lRoom')}]")

}
