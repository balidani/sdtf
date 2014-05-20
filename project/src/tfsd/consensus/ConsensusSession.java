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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Channel;
import net.sf.appia.core.Direction;
import net.sf.appia.core.Event;
import net.sf.appia.core.Layer;
import net.sf.appia.core.Session;
import net.sf.appia.core.events.SendableEvent;
import tfsd.ProcessInitEvent;
import tfsd.ProcessSet;
import tfsd.SampleAppl;
import tfsd.SampleProcess;

/**
 * Session implementing the Randomized Consensus Protocol
 * 
 */
public class ConsensusSession extends Session {

	public static final int PHASE_1 = 1;
	public static final int PHASE_2 = 2;
	public static final int PHASE_DECIDE = 3;

	private ProcessSet processes;
	
	private Map<Integer, ProposeEvent> phaseOneQuorum;
	private Map<Integer, ProposeEvent> phaseTwoQuorum;
	
	private List<ProposeEvent> phaseTwoQueue;
	
	private int startedTimestamp;
	private int decidedTimestamp;

	/**
	 * Builds a new BEBSession.
	 * 
	 * @param layer
	 */
	public ConsensusSession(Layer layer) {
		super(layer);

		phaseOneQuorum = new HashMap<Integer, ProposeEvent>();
		phaseTwoQuorum = new HashMap<Integer, ProposeEvent>();
		
		phaseTwoQueue = new ArrayList<ProposeEvent>();
		
		startedTimestamp = 0;
		decidedTimestamp = 0;
	}

	/**
	 * Handles incoming events.
	 * 
	 * @see appia.Session#handle(appia.Event)
	 */
	public void handle(Event event) {

		try {
			if (event instanceof DecideEvent) {
				handleDecide((DecideEvent) event);
			} else if (event instanceof ProposeEvent) {
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
		int proposedInteger = 0;
		try {
			 proposedInteger = Integer.parseInt(proposed);
		} catch (NumberFormatException ex) {
			System.out.println("RC: Error parsing proposed value");
		}
		
		startedTimestamp++;
		sendProposal(event.getSourceSession(), event.getChannel(), PHASE_1, proposedInteger, event.getDir(), true);
		
//		if (!phaseTwoQueue.isEmpty()) {
//			ProposeEvent proposal = phaseTwoQueue.remove(0);
//			proposal.init();
//			proposal.go();
//		}

	}

	private void handlePropose(ProposeEvent event) throws AppiaEventException {
		// TODO implement this

		// When we receive the proposal, we must broadcast it
		if (event.getDir() == Direction.DOWN) {
			event.go();
			return;
		}

		// Set the value on the proposed object, for convenience
		event.setTimestamp(event.getMessage().popInt());
		event.setValue(event.getMessage().popInt());
		event.setPhase(event.getMessage().popInt());
		
		// Ignore events with a lower timestamp than the current one
		if (event.getTimestamp() <= decidedTimestamp) {
			System.out.printf("RC: Timestamp %d was already decided, aborting (%d)\n", event.getTimestamp(), event.getPhase());
			return;
		}

		// Check the phase for the Proposal
		if (event.getPhase() == PHASE_1) {

			System.out.println("RC: (PHASE 1) Received incoming ProposeEvent: " + event.getValue());
			handleProposePhase1(event);
		} else if (event.getPhase() == PHASE_2) {

			System.out.println("RC: (PHASE 2) Received incoming ProposeEvent: " + event.getValue());
			handleProposePhase2(event);
		} else if (event.getPhase() == PHASE_DECIDE) {
			
			System.out.println("RC: (DECISION) Sending decision with value " + event.getValue());

			decidedTimestamp = startedTimestamp;
			sendDecision(this, event.getChannel(), event.getValue(), Direction.DOWN);
		}
	}

	private void handleProposePhase1(ProposeEvent event)
			throws AppiaEventException {

		// First we must wait for a quorum

		// Save each incoming value according to its process id
		SampleProcess pi = processes.getProcess((SocketAddress) event.source);
		int processId = pi.getProcessNumber();
		phaseOneQuorum.put(processId, event);

		// When we get a majority, check if the values are identical
		if (phaseOneQuorum.size() > processes.getSize() / 2) {
			
			ProposeEvent firstProposal = phaseOneQuorum.values().iterator().next();
			int firstValue = firstProposal.getValue();
			boolean identicalValues = true;
			for (ProposeEvent proposed : phaseOneQuorum.values()) {
				if (proposed.getValue() != firstValue) {
					identicalValues = false;
					break;
				}
			}

			// If the values are identical, broadcast this v* value and enter phase 2
			// Otherwise broadcast a default value and enter phase 2
			int broadcastedValue;

			if (identicalValues) {
				System.out.println("RC: A quorum was found with identical values");
				broadcastedValue = firstValue;
			} else {
				System.out.println("RC: A quorum was found with different values");

				// TODO: add a flag instead
				broadcastedValue = -1;
			}

			// Send the broadcast for phase 2

			if (startedTimestamp < event.getTimestamp()) {
				ProposeEvent queuedProposal = sendProposal(this, event.getChannel(), PHASE_2, broadcastedValue, Direction.DOWN, true);
				phaseTwoQueue.add(queuedProposal);
			} else {
				sendProposal(this, event.getChannel(), PHASE_2, broadcastedValue, Direction.DOWN, true);
			}
		}
	}

	private void handleProposePhase2(ProposeEvent event) throws AppiaEventException {
		
		// Save each incoming value according to its process id
		SampleProcess pi = processes.getProcess((SocketAddress) event.source);
		int processId = pi.getProcessNumber();
		phaseTwoQuorum.put(processId, event);
		
		// When we get a quorum, check if the values are identical
		if (phaseTwoQuorum.size() == processes.getSize() - SampleAppl.TOLERATED_FAILURES) {
			
			System.out.println("RC: Got a quorum for phase 2");
			
			// Try to find f+1 values of v*
			int count = 0;
			int vStar = -1;
			
			for (ProposeEvent proposed : phaseTwoQuorum.values()) {
				if (proposed.getValue() != -1) {
					vStar = proposed.getValue();
					count++;
				}
			}
			
			if (count > SampleAppl.TOLERATED_FAILURES) {
				// Found f+1 processes proposing v*, _reliable_ broadcast and decide

				phaseOneQuorum.clear();
				phaseTwoQuorum.clear();
				
				sendProposal(this, event.getChannel(), PHASE_DECIDE, vStar, Direction.DOWN, true);
				
			} else if (count > 0) {
				// Start new round with v*

				phaseOneQuorum.clear();
				phaseTwoQuorum.clear();
				sendProposal(this, event.getChannel(), PHASE_1, vStar, Direction.DOWN, true);
				
			} else {
				// Start new round with coin toss
				// Pick a random value from the first quorum
				// TODO: Maybe keep a set of values which is persistent between rounds
				// (it's not emptied after each round)
				
				boolean foundProposal = false;
				int randomValue = phaseOneQuorum.values().iterator().next().getValue();
				
				while (!foundProposal) {
					int randomIndex = (new Random()).nextInt() % phaseOneQuorum.size();
					if (phaseOneQuorum.containsKey(randomIndex)) {
						ProposeEvent randomProposal = (ProposeEvent) phaseOneQuorum.get(randomIndex);
						randomValue = randomProposal.getValue();
						foundProposal = true;
					}
				}
				
				phaseOneQuorum.clear();
				phaseTwoQuorum.clear();

				sendProposal(this, event.getChannel(), PHASE_1, randomValue, Direction.DOWN, true);
			}
		}
	}
	
	private void handleDecide(DecideEvent event) {
		// Dummy handler, maybe decide should not be broadcasted?
		if (event.getSourceSession() != null) {
			System.out.println("RC: Decided on value " + event.getMessage().peekInt());
		}
	}
	
	/*
	 * Utils 
	 */
	
	private ProposeEvent sendProposal(Session source, Channel channel, int phase, int value, int dir, boolean send) throws AppiaEventException {
		
		ProposeEvent proposal = new ProposeEvent();
		proposal.getMessage().pushInt(phase);
		proposal.getMessage().pushInt(value);
		proposal.getMessage().pushInt(startedTimestamp);

		proposal.setSourceSession(source);
		proposal.setChannel(channel);
		proposal.setDir(dir);

		if (send) {
			proposal.init();
			proposal.go();
		}
		
		return proposal;		
	}
	
	private void sendDecision(Session source, Channel channel, int value, int dir) throws AppiaEventException {
		
		DecideEvent decision = new DecideEvent();
		decision.getMessage().pushInt(value);

		decision.setSourceSession(source);
		decision.setChannel(channel);
		decision.setDir(dir);

		decision.init();
		decision.go();
	}

}
