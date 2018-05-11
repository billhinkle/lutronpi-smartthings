/**
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Based on SmartThings "Momentary Button Tile" device handler and "Lutron Scene" by Nate Schwartz
 *		/ contributions marked wjh by Bill Hinkle (github: billhinkle) 2018
 *	v2.0.0.00	2018-04-22	wjh	Initial version: support for triggering of SmartThings functions by Lutron scene
 *										Added rename reporting to parent
 */
metadata {
	definition (name: "Lutron Scene", namespace: "lutronpi", author: "Bill Hinkle") {
		capability "Actuator"
		capability "Momentary"
		capability "Button"
        capability "Switch"	// allows Lutron scenes to be selected as components of SmartThings scenes

		command "appPush"

		attribute "transition", "enum", ["><", "quiescent"]
	}

	// simulator metadata
	simulator {
		status "Triggered":  "data: {action: pushed}"
	}

	// UI tile definitions
	tiles (scale:2) {
		standardTile("scene", "transition", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true) {
			state "quiescent", label: 'SCENE', defaultState: true, backgroundColor: "#ffffff", nextState: "><", action: "appPush"
			state "><", label: '${currentValue}', backgroundColor: "#808080", nextState: "quiescent"
		}

		valueTile("lInfo", "lutronInfo", width: 5, height: 1, decoration: "flat") {
			state "lutronInfo", label: '${currentValue}', backgroundColor: "#ffffff"
		}
        
		main "scene"
		details(["scene","lInfo"])
	}
}

def parse(description) {
	log.debug "${device.label} parses: " + description
	if (!(description instanceof java.util.Map) || !description.containsKey('name') || !description.containsKey('value'))
		return

	def action = description.value
	if (action == 'pushed') {
			return createEvent(name: 'button', value: 'pushed', data: [buttonNumber: 1], descriptionText: "${device.label} scene was triggered", isStateChange: true)
	}
}

def push() {
	appPush()
}

def on() {
	log.debug "Scene ${device.label} got an on()"
    appPush()
}

def off() {
	log.debug "Scene ${device.label} got an off()"
}

def appPush() {
	log.debug "Scene ${device.label} was triggered"
	parent.runScene(this)
//	log.debug "Scene ${device.label} returned from parent.runScene()"    
	sendEvent(name: "transition", value: "quiescent", isStateChange: true)
}

def installed() {
	state.label = device.label

	def N_BUTTONS = 1
	sendEvent(name: "numberOfButtons", value: N_BUTTONS)

	log.info "Lutron Scene ${device.label} installed"

	initialize()
}

def updated() {
	log.info "Lutron Scene ${device.label} prefs updated"
	initialize()
}

def initialize() {
   if (state.label != device.label) {
		log.info "Lutron Scene ${state.label} renamed to ${device.label}"
		state.label = device.label
		parent.renameChildDevice(this, device.label);
    }

	def dni = device.deviceNetworkId.tokenize('.')
	sendEvent (name: "lutronInfo", value: "Lutron ${dni[0]} Scene:${dni[1]}")

}
