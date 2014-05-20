/*
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 *
 * Contact
 * 	Address:
 *		Rua Alves Redol 9, Office 605
 *		1000-029 Lisboa
 *		PORTUGAL
 * 	Email:
 * 		ler@ist.utl.pt
 * 	Web:
 *		http://homepages.gsd.inesc-id.pt/~ler/
 * 
 */

package tfsd.consensus;

import tfsd.SampleApplSession;
import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;

/**
 * Session implementing the Randomized Consensus Protocol
 * 
 */
public class ReliableConsensusSession extends Session {

	/**
	 * Builds a new session.
	 * 
	 * @param layer
	 */
	protected ReliableConsensusSession(Layer layer) {
		super(layer);
	}

	/**
	 * Handles incoming events.
	 * 
	 * @see appia.Session#handle(appia.Event)
	 */
	public void handle(Event event) {

		try {
			if (event instanceof DecideEvent) {
				handleDecideEvent((DecideEvent) event);
			}
			else if (event instanceof SendableEvent) {
				handleSendableEvent((SendableEvent) event);
			} else {
				event.go();
			}
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handleDecideEvent(DecideEvent event) {
		
		// Dummy handler, maybe decide should not be broadcasted?
		if (event.getSourceSession() != null) {
			SampleApplSession.instance.decide(event.getMessage().peekInt());
		}
	}

	private void handleSendableEvent(SendableEvent event)
			throws AppiaEventException {
		
		int value = event.getMessage().peekInt();
		System.out.println("Received message " + value);
			
		// Convert the sample sendable into a decide event and forward it
		DecideEvent decision = new DecideEvent();
		decision.getMessage().pushInt(value);

		decision.setSourceSession(this);
		decision.setChannel(event.getChannel());
		decision.setDir(Direction.DOWN);

		decision.init();
		decision.go();
	}

}
