/*
 *
 * Hands-On code of the book Introduction to Reliable Distributed Programming
 * by Christian Cachin, Rachid Guerraoui and Luis Rodrigues
 * Copyright (C) 2005-2011 Luis Rodrigues
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

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;


/**
 * Session implementing the Basic Broadcast protocol.
 * 
 * @author nuno
 * 
 */
public class ConsensusSession extends Session {

    /**
     * Builds a new BEBSession.
     * 
     * @param layer
     */
    public ConsensusSession(Layer layer) {
        super(layer);
    }

    /**
     * Handles incoming events.
     * 
     * @see appia.Session#handle(appia.Event)
     */
    public void handle(Event event) {
        // Init events. Channel Init is from Appia and ProcessInitEvent is to know
        // the elements of the group
        if (event instanceof ProposeEvent) {
            handlePropose((ProposeEvent) event);
        } else if (event instanceof SendableEvent) {
        	handleSendable((SendableEvent) event);
        } else {
            try {
				event.go();
			} catch (AppiaEventException e) {
				e.printStackTrace();
			}
        }
    }

	private void handleSendable(SendableEvent event) {
		
		String proposed = event.getMessage().popString();
		System.out.println("Received proposed value: " + proposed);
		
		ProposeEvent proposal = new ProposeEvent();
		proposal.getMessage().pushString(proposed);

		proposal.setSourceSession(event.getSourceSession());
		proposal.setChannel(event.getChannel());
		proposal.setDir(event.getDir());
		
		try {

			proposal.init();
			proposal.go();
			
		} catch (AppiaEventException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	private void handlePropose(ProposeEvent event) {
		// TODO implement this
		System.out.println("Received a ProposeEvent, direction: " + event.getDir());
		
		try {
			event.go();
		} catch (AppiaEventException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
