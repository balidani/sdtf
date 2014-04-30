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

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;
import tfsd.ProcessInitEvent;
import tfsd.ProcessSet;
import tfsd.SampleProcess;

/**
 * Session implementing the Basic Broadcast protocol.
 * 
 * @author nuno
 * 
 */
public class ConsensusSession extends Session {

	public static final int PHASE_1 = 1;
	public static final int PHASE_2 = 2;

	private ProcessSet processes;
	private Map<Integer, ProposeEvent> quorum;

	/**
	 * Builds a new BEBSession.
	 * 
	 * @param layer
	 */
	public ConsensusSession(Layer layer) {
		super(layer);

		quorum = new HashMap<Integer, ProposeEvent>();
	}

	/**
	 * Handles incoming events.
	 * 
	 * @see appia.Session#handle(appia.Event)
	 */
	public void handle(Event event) {

		try {
			if (event instanceof ProposeEvent) {
				handlePropose((ProposeEvent) event);
			} else if (event instanceof SendableEvent) {
				handleSendable((SendableEvent) event);
			} else if (event instanceof ProcessInitEvent) {
				handleProcessInitEvent((ProcessInitEvent) event);
			} else {
				event.go();
			}
		} catch (AppiaEventException e) {
			e.printStackTrace();
		}
	}

	private void handleProcessInitEvent(ProcessInitEvent event)
			throws AppiaEventException {

		processes = event.getProcessSet();
		event.go();
	}

	private void handleSendable(SendableEvent event) throws AppiaEventException {

		String proposed = event.getMessage().popString().trim();
		System.out.println("RC: Received proposed value: " + proposed);

		// Convert to integer
		int proposedInteger = Integer.parseInt(proposed);

		// Create a ProposeEvent
		ProposeEvent proposal = new ProposeEvent();

		// Push the phase
		proposal.getMessage().pushInt(PHASE_1);
		// Push the value proposed
		proposal.getMessage().pushInt(proposedInteger);

		proposal.setSourceSession(event.getSourceSession());
		proposal.setChannel(event.getChannel());
		proposal.setDir(event.getDir());

		proposal.init();
		proposal.go();

	}

	private void handlePropose(ProposeEvent event) throws AppiaEventException {
		// TODO implement this

		// When we receive the proposal, we must broadcast it
		if (event.getDir() == Direction.DOWN) {
			event.go();
			return;
		}

		// Set the value on the proposed object, for convenience
		event.setValue(event.getMessage().popInt());
		event.setPhase(event.getMessage().popInt());

		// Check the phase for the Proposal
		if (event.getPhase() == PHASE_1) {

			System.out.println("RC: (PHASE 1) Received incoming ProposeEvent");
			handleProposePhase1(event);
		} else if (event.getPhase() == PHASE_2) {

			System.out.println("RC: (PHASE 2) Received incoming ProposeEvent");
			handleProposePhase2(event);
		}
	}

	private void handleProposePhase1(ProposeEvent event)
			throws AppiaEventException {

		// First we must wait for a quorum

		// Save each incoming value according to its process id
		SampleProcess pi = processes.getProcess((SocketAddress) event.source);
		int processId = pi.getProcessNumber();
		quorum.put(processId, event);

		// When we get a quorum, check if the values are identical
		if (quorum.size() > processes.getSize() / 2) {
			ProposeEvent firstProposal = quorum.values().iterator().next();
			int firstValue = firstProposal.getValue();
			boolean identicalValues = true;
			for (ProposeEvent proposed : quorum.values()) {
				if (proposed.getValue() != firstValue) {
					identicalValues = false;
					break;
				}
			}

			// If the values are identical, broadcast this v* value and enter
			// phase 2
			// Otherwise broadcast a default value and enter phase 2
			int broadcastedValue;

			if (identicalValues) {
				System.out
						.println("RC: A quorum was found with identical values");
				broadcastedValue = firstValue;
			} else {
				System.out
						.println("RC: A quorum was found with different values");

				// TODO: add a flag instead
				broadcastedValue = -1;
			}

			// Send the broadcast for phase 2
			ProposeEvent proposal = new ProposeEvent();
			proposal.getMessage().pushInt(PHASE_2);
			proposal.getMessage().pushInt(broadcastedValue);

			proposal.setSourceSession(event.getSourceSession());
			proposal.setChannel(event.getChannel());
			proposal.setDir(Direction.DOWN);

			proposal.init();
			proposal.go();
		}
	}

	private void handleProposePhase2(ProposeEvent event) throws AppiaEventException {
		
		System.out.println("Got this far...");
	}

}
