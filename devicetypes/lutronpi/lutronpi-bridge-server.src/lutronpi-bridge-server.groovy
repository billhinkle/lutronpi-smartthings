/**
 *  LutronPi Bridge Server Device Type
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
 *  Based on SmartThings example LAN device handler and "Raspberry Pi Lutron Caseta" by Nate Schwartz njschwartz
 *		/ contributions marked wjh by Bill Hinkle (github: billhinkle)
 *	v1.0.0.00	2018-05-01	wjh	Initial version: IP:port to common format; support for bridge status polling and bridge data display
 *	v1.0.0.01	2018-10-05	wjh	Added a contact capability for external monitoring of status: online (closed) vs offline (open)
 */

metadata {
	definition (name: "LutronPi Bridge Server", namespace: "lutronpi", author: "Bill Hinkle") {
		capability "Sensor"
		capability "Bridge"
		capability "Polling"
		capability "Refresh"
		capability "Contact Sensor"	// attribute:[contact:[action:[closed,open]]] closed=online/open=offline
        
		command "sync"
        command "bridgeUpdate"

		attribute "commStatus", "string"
		attribute "networkAddress", "string"
		attribute "bridgeInfo", "string"
	}

	simulator {
	}

	tiles(scale: 2) {
		standardTile('mainTile', 'commStatus', decoration: 'flat', height: 1, width: 6) {
        	state 'default', label: '', icon: 'st.Lighting.light99-hue', backgroundColor: '#ffffff', defaultState: true
			state 'ONLINE', label: '${name}', icon: 'st.Lighting.light99-hue', backgroundColor: '#79b821'
            state 'OFFLINE', label: '${name}', icon: 'st.Lighting.light99-hue', backgroundColor: '#dc243e'
        }
		multiAttributeTile(name:'topTile', type: "generic") {
			tileAttribute ('commStatus', key: 'PRIMARY_CONTROL') {
				attributeState '', label: 'LutronPi', action: '', icon: 'st.Lighting.light99-hue', backgroundColor: '#F3C200'
			}
			tileAttribute ('networkAddress', key: 'SECONDARY_CONTROL') {
				attributeState 'default', label: '${currentValue}'
			}
		}
		valueTile('bridgeInfo', 'bridgeInfo', decoration: 'flat', height: 5, width: 6) {
			state 'bridgeInfo', label:'═════Bridges═════\n${currentValue}'
		}

		main (['mainTile'])
		details(['topTile', 'bridgeInfo'])
	}
}

// LAN events from the Lutron Pi server are routed here
def parse(description) {
//	log.debug "${device.label}: " + description
	def msg = parseLanMessage(description)
        
//	if (msg?.CommuniqueType =="ReadResponse" && msg?.Header?.MessageBodyType == "LutronPiSummary") {
//		lutronUpdate(msg.body)
//        return
//	}      
        
	def bodyString = msg.body
	if (bodyString) {
		log.debug "${device.label} LAN msg body: $bodyString"
		def json = msg.json
		// Send all data to the parent parse method for event generation
		if (json) {
//			log.debug "${device.label} JSON received: ${json}"
			parent.parse(json) 
		}
	}
}

def sync(ip, port) {
    def nwAText = 'OFFLINE'
	if (ip) {
		sendEvent(name: 'commStatus', value: 'ONLINE')
		sendEvent(name: 'contact', value: 'closed')
		updateDataValue('ip', ip as String)
        nwAText = "IP: ${ip}"
		if (port) {
			updateDataValue('port', port as String)
            nwAText += ":${port}"
		}
	}
    else {
    	sendEvent(name: 'commStatus', value: 'OFFLINE')
		sendEvent(name: 'contact', value: 'open')
		sendEvent(name: 'bridgeInfo', value: '');
	}
	log.info "Got a sync update from SSDP: $nwAText}"
	sendEvent(name: 'networkAddress', value: nwAText);
}

def bridgeUpdate(bridgeData) {
//	parent.lutronPiPollResponse(bridgeData)	// SmartThings polling is too unreliable; now handled in SmartApp

	def bInfo = ''
	def bridgeMap = bridgeData?.Bridges
    def bridgeCnt = bridgeMap?.size() ?: 0
    if (bridgeCnt) {
		bridgeMap.each { b, p ->
			bInfo += ((p.BridgeBrand?p.BridgeBrand.take(6):'') + '\u200A' + (b as String)).padRight(16,'\u202F') + '\u200A' + (p.Connected ? p.DeviceType : 'offline').padLeft(13,'\u202F') + '\n' // \u2002
		}
    }
	for (def bi = bridgeCnt; bi < 8; bi++)	// if 8 bridges isn't enough, here's the place to change that
		bInfo += '—\n'
	sendEvent(name: 'bridgeInfo', value: bInfo);
}

def poll() {
//	parent.pollLutronPi()	// SmartThings polling is too unreliable; now handled in SmartApp
}

def installed() {
	updated()
}

def updated() {
	sync(getDataValue('ip'),getDataValue('port'))
}
