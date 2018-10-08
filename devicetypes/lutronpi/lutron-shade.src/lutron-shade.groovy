/**
 *	Lutron Switch Device Type
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
 *	v2.0.0.00	2018-05-10	wjh	Initial version: modified dimmer to switch, added Lutron device info
 *										Added rename reporting to parent
 *	v2.0.0.01	2018-10-05	wjh	tweaked Lutron info footer
 */
 metadata {
        definition (name: "Lutron Switch", namespace: "lutronpi", author: "Bill Hinkle") {
		capability "Actuator"
        capability "Sensor"
		capability "Switch"
        capability "Refresh"

        attribute "lutronInfo", "string"
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc", nextState:"\u25BC" //"▼"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"\u25B2" //"▲"
				attributeState "\u25B2", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc", nextState:"\u25BC" //"▼"
				attributeState "\u25BC", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"\u25B2" //"▲"
            }
		}
		standardTile("switchRefresh", "device.refresh", width: 1, height: 1, decoration: "flat") {
			state "default", label: '', icon:"st.secondary.refresh", action: "refresh.refresh"
		}
		valueTile("lInfo", "lutronInfo", width: 5, height: 1, decoration: "flat") {
			state "lutronInfo", label: '${currentValue}', backgroundColor: "#ffffff"
		}

		main "switch"
        details(["switch", "lInfo", "switchRefresh"])
    }
}

def parse(description) {
//	log.debug "${device.label} parses: " + description

	if (!(description instanceof java.util.Map) || !description.containsKey('name') || !description.containsKey('value'))
		return

    if (description.name == 'level') {
		def level = description.value?:0 as Integer
		sendEvent(name: 'switch', value: ((level > 0)?'on':'off'))
	}
}

def on() {
	parent.on(this)
//	wait for Lutron to update this state, to avoid race
//	sendEvent(name: "switch", value: "on")
	log.info "Switch ${device.label} On"
}

def off() {
	parent.off(this)
//	wait for Lutron to update this state, to avoid race
//	sendEvent(name: "switch", value: "off")
	log.info "Switch ${device.label} Off"
}


def refresh() {
	log.info "Switch ${device.label} refresh"
    parent.refresh(this)
}

def installed() {
	state.label = device.label
    log.info "Switch ${device.label} installed" 
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
    if (state.label != device.label) {
    	log.info "Switch ${state.label} renamed to ${device.label}"
		state.label = device.label
		parent.renameChildDevice(this, device.label);
   }

	refresh()
    
	def lRoom = device.getDataValue('lRoom')
	def lInfoText = "Lutron ${device.deviceNetworkId.tokenize('.')[0]} devID:${device.getDataValue('lipID')} Zone:${device.getDataValue("zone")}" + (lRoom?"\n[$lRoom]":'')
	sendEvent (name: "lutronInfo", value: lInfoText)
}
