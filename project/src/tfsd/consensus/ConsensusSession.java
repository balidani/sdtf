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
import net.sf.appia.core.message.Message;
import tfsd.ProcessInitEvent;
import tfsd.SampleAppl;
import tfsd.SampleApplSession;
import tfsd.SampleProcess;
import tfsd.lrb.ProcessSet;

/**
 * Session implementing the Randomized Consensus Protocol
 * 
 */
public class ConsensusSession extends Session {

	public static final int PHASE_1 = 1;
	public static final int PHASE_2 = 2;
	public static final int PHASE_DECIDE = 3;

	private ProcessSet processes;

	// Storing the unprocessed proposals
	List<ProposeEvent> proposalQueue;
	
	// Storing the received decide events for future startedTimestamp values
	Map<Integer, DecideEvent> decisionQueue;

	private int startedTimestamp;
	private int decidedTimestamp;
	private int phaseTimestamp;

	/**
	 * Builds a new ConsensusSession.
	 * 
	 * @param layer
	 */
	public ConsensusSession(Layer layer) {
		super(layer);

		proposalQueue = new ArrayList<ProposeEvent>();
		decisionQueue = new HashMap<Integer, DecideEvent>();

		startedTimestamp = 0;
		decidedTimestamp = 0;
		phaseTimestamp = 0;
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

	private synchronized void handleSendable(SendableEvent event)
			throws AppiaEventException {

		String proposed = event.getMessage().popString().trim();
		System.err.println("RC: Received proposed value: " + proposed);

		// Convert to integer
		int proposedInteger = 0;
		try {
			proposedInteger = Integer.parseInt(proposed);
		} catch (NumberFormatException ex) {
			System.err.println("RC: Error parsing proposed value");
		}

		startedTimestamp++;
		phaseTimestamp = 0;

		// If we already know this instance is decided, act accordingly
		if (decisionQueue.containsKey(startedTimestamp)) {
			decide(decisionQueue.get(startedTimestamp));
		} else {
			sendProposal(event.getSourceSession(), event.getChannel(), PHASE_1,
				proposedInteger, event.getDir());
		}

	}

	private synchronized void handleDecide(DecideEvent event) {

		if (event.getDir() == Direction.UP) {

			int timestamp = event.getMessage().popInt();
			int value = event.getMessage().popInt();
			
			event.setTimestamp(timestamp);
			event.setValue(value);
			
			if (timestamp == startedTimestamp && timestamp > decidedTimestamp) {
				decide(event);
			} else {
				decisionQueue.put(timestamp, event);
			}
		}
	}
	
	private synchronized void decide(DecideEvent event) {
		
		System.err.printf("*** DECIDING %d *** %d, %d, %d\n", event.getValue(),
				event.getTimestamp(), startedTimestamp, decidedTimestamp);

		decidedTimestamp++;
		SampleApplSession.instance.decide(event.getValue());
	}

	private synchronized void handlePropose(ProposeEvent event)
			throws AppiaEventException {

		// When we receive the proposal, we must broadcast it
		if (event.getDir() == Direction.DOWN) {
			event.go();
			return;
		}

		// Set the value on the proposed object, for convenience
		event.setPhaseTimestamp(event.getMessage().popInt());
		event.setTimestamp(event.getMessage().popInt());
		event.setValue(event.getMessage().popInt());
		event.setPhase(event.getMessage().popInt());
		
		// Set the process id
		SampleProcess pi = processes.getProcess((SocketAddress) event.source);
		event.setProcess(pi.getProcessNumber());

		// Ignore events with a lower timestamp than the current one
		if (event.getTimestamp() <= decidedTimestamp) {
			// System.err.printf("RC: Timestamp %d was already decided, aborting (%d)\n",
			// event.getTimestamp(), event.getPhase());
			return;
		}

		proposalQueue.add(event);

		// Check the phase for the Proposal
		if (event.getPhase() == PHASE_1) {
			handleProposePhase1(event);
		} else if (event.getPhase() == PHASE_2) {
			handleProposePhase2(event);
		}
	}

	private synchronized void handleProposePhase1(ProposeEvent event)
			throws AppiaEventException {

		System.err.printf("RC: (PHASE 1) Received incoming ProposeEvent: %d @%d, from %d\n",
			event.getValue(), event.getTimestamp(), event.getProcess());
		
		// First we must wait for a quorum
		Map<Integer, ProposeEvent> quorum = getQuorum(PHASE_1);

		// When we get a majority, check if the values are identical
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

			// If the values are identical, broadcast this v* 
			// value and enter phase 2. Otherwise broadcast a 
			// default value and enter phase 2
			int broadcastedValue;

			if (identicalValues) {
				System.err
						.printf("RC: (PHASE 1) A quorum was found with identical values (%d)\n",
								firstValue);
				broadcastedValue = firstValue;
			} else {
				System.err
						.printf("RC: (PHASE 1) A quorum was found with different values: [");
				for (ProposeEvent proposed : quorum.values()) {
					System.err.printf("%d, ", proposed.getValue());
				}
				System.err.println("]");

				// TODO: add a flag instead
				broadcastedValue = -1;
			}
			
			// Send the broadcast for phase 2
			sendProposal(this, event.getChannel(), PHASE_2, broadcastedValue,
					Direction.DOWN);
		}
	}

	private synchronized void handleProposePhase2(ProposeEvent event)
			throws AppiaEventException {

		System.err.printf("RC: (PHASE 2) Received incoming ProposeEvent: %d @%d, from %d\n",
				event.getValue(), event.getTimestamp(), event.getProcess());
		
		// First we must wait for a quorum
		Map<Integer, ProposeEvent> quorum = getQuorum(PHASE_2);
		
		// When we get a quorum, check if the values are identical
		if (quorum.size() == processes.getSize()
				- SampleAppl.TOLERATED_FAILURES) {

			System.err.print("RC: Got a quorum for phase 2 [");
			for (ProposeEvent proposed : quorum.values()) {
				System.err.print(proposed.getValue() + ", ");
			}
			System.err.println("]");

			// Try to find f+1 values of v*
			int count = 0;
			int vStar = -1;

			for (ProposeEvent proposed : quorum.values()) {
				if (proposed.getValue() != -1) {
					vStar = proposed.getValue();
					count++;
				}
			}

			if (count > SampleAppl.TOLERATED_FAILURES) {
				// Found f+1 processes proposing v*, _reliable_ broadcast
				// and decide

				System.err.println("RC: (PHASE 2) starting decide with " + vStar);
				
				clearQuorums();
				
				phaseTimestamp++;
				sendDecision(this, event.getChannel(), PHASE_DECIDE, vStar,
						Direction.DOWN);

			} else if (count > 0) {
				// Start new round with v*

				System.err.println("RC: (PHASE 2) starting phase 1 with (star) " + vStar);
				
				clearQuorums();
				
				phaseTimestamp++;
				sendProposal(this, event.getChannel(), PHASE_1, vStar,
						Direction.DOWN);

			} else {
				// Start new round with coin toss
				// Pick a random value from the first quorum

				Map<Integer, ProposeEvent> firstQuorum = getQuorum(PHASE_1);
				boolean foundProposal = false;
				int randomValue = firstQuorum.values().iterator().next().getValue();

				while (!foundProposal) {
					int randomIndex = (new Random()).nextInt()
							% firstQuorum.size();
					if (firstQuorum.containsKey(randomIndex)) {
						ProposeEvent randomProposal = (ProposeEvent) firstQuorum
								.get(randomIndex);
						randomValue = randomProposal.getValue();
						foundProposal = true;
					}
				}

				System.err.println("RC: (PHASE 2) starting phase 1 with (random) " + randomValue);

				clearQuorums();
				
				phaseTimestamp++;
				sendProposal(this, event.getChannel(), PHASE_1, randomValue,
						Direction.DOWN);
			}
		}
	}

	/*
	 * Utils
	 */

	private synchronized Map<Integer, ProposeEvent> getQuorum(int phase) {
		Map<Integer, ProposeEvent> quorum = new HashMap<Integer, ProposeEvent>();
		
		for (ProposeEvent oldProposal : proposalQueue) {
			// Same phase
			if (oldProposal.getPhase() != phase)
				continue;
			
			// Same timestamp as our value
			if (oldProposal.getTimestamp() != startedTimestamp)
				continue;
			
			// Same phase timestamp as our phase's
			if (oldProposal.getPhaseTimestamp() != phaseTimestamp)
				continue;
			
			// Distinct processes in the quorum
			quorum.put(oldProposal.getProcess(), oldProposal);
		}
		
		return quorum;
	}

	private synchronized void clearQuorums() {
		clearQuorum(getQuorum(PHASE_1));
		clearQuorum(getQuorum(PHASE_2));
	}
	
	private synchronized void clearQuorum(Map<Integer, ProposeEvent> quorum) {
		// Remove members of the quorum
		for (ProposeEvent quorumMember : quorum.values()) {
			proposalQueue.remove(quorumMember);
		}
	}

	private synchronized void sendProposal(Session source, Channel channel,
			int phase, int value, int dir) throws AppiaEventException {

		ProposeEvent proposal = new ProposeEvent();
		Message msg = proposal.getMessage();
		msg.pushInt(phase);
		msg.pushInt(value);
		msg.pushInt(startedTimestamp);
		msg.pushInt(phaseTimestamp);

		proposal.setSourceSession(source);
		proposal.setChannel(channel);
		proposal.setDir(dir);

		proposal.init();
		proposal.go();
	}

	private synchronized void sendDecision(Session session, Channel channel,
			int phase, int vStar, int down) throws AppiaEventException {

		DecideEvent decision = new DecideEvent();
		Message msg = decision.getMessage();
		msg.pushInt(phase);
		msg.pushInt(vStar);
		msg.pushInt(startedTimestamp);

		decision.setSourceSession(session);
		decision.setChannel(channel);
		decision.setDir(Direction.DOWN);

		decision.init();
		decision.go();
	}
	
}
